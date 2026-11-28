package vn.englishconversation.support.service;

import static vn.englishconversation.support.dto.SupportData.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.admin.repository.AdminAudit;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.support.repository.SupportRepository.TicketState;
import vn.englishconversation.learning.repository.LearningStore;
import vn.englishconversation.support.repository.SupportRepository;

@Service
@FeatureEnabled(FeatureEnabled.Feature.SUPPORT)
public class SupportService {
  private static final Pattern EMAIL = Pattern.compile("(?iu)[\\p{L}\\p{N}._%+\\-]+@[\\p{L}\\p{N}.\\-]+\\.[\\p{L}]{2,}");
  private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])\\+?\\d(?:[ .()\\-]*\\d){6,}(?![\\p{L}\\p{N}])");
  private static final Pattern URL = Pattern.compile("(?i)https?://\\S+");
  private static final Pattern SECRET = Pattern.compile("(?i)(?:password|api[ _-]?key|token|mật khẩu)\\s*[:=]\\s*\\S+");
  private final SupportRepository repository;
  private final LearningStore learning;
  private final AccountService accounts;
  private final AdminAudit audit;

  public SupportService(SupportRepository repository, LearningStore learning, AccountService accounts, AdminAudit audit) {
    this.repository = repository;
    this.learning = learning;
    this.accounts = accounts;
    this.audit = audit;
  }

  @Transactional(readOnly=true)
  public Preview preview(AccountPrincipal actor, Source source) {
    return preview(actor.id(),source,false);
  }

  private Preview preview(long owner, Source source, boolean lock) {
    String value = repository.source(owner,source,lock)
        .orElseThrow(() -> new ApiException(404,"REPORT_SOURCE_NOT_FOUND","Nguồn báo cáo không còn khả dụng."));
    String excerpt = redact(value);
    if (excerpt.codePointCount(0,excerpt.length()) > 480)
      excerpt = excerpt.substring(0,excerpt.offsetByCodePoints(0,480)) + "…";
    return new Preview(excerpt, hash(owner + ":" + source.type() + ":" + source.id() + ":" + excerpt));
  }

  @Transactional
  public Ticket create(AccountPrincipal actor, Create input) {
    lockLearner(actor);
    if ((input.kind() == Kind.CONTENT_REPORT) != (input.source() != null)
        || input.source() == null && input.previewRevision() != null)
      throw new ApiException(400,"INVALID_REPORT_SOURCE","Báo cáo nội dung cần đúng một nguồn. Phiếu thường gửi trực tiếp.");
    String title = redact(input.title().strip());
    String body = redact(input.body().strip());
    String fingerprint = hash(input.toString());
    var prior = repository.prior(actor.id(),input.clientRequestId().toString());
    if (prior.isPresent()) {
      if (!fingerprint.equals(prior.get().requestFingerprint())) throw reused();
      return SupportRepository.view(prior.get());
    }
    String excerpt = null;
    if (input.source() != null) {
      Preview current = preview(actor.id(),input.source(),true);
      if (!current.revision().equals(input.previewRevision()))
        throw new ApiException(409,"REPORT_PREVIEW_CHANGED","Hãy xem lại đoạn trích trước khi gửi.");
      excerpt = current.excerpt();
    }
    long ticket = repository.create(actor.id(),input,title,excerpt,fingerprint);
    repository.addMessage(ticket,actor.id(),"LEARNER",body,input.clientRequestId().toString());
    return SupportRepository.view(ticket(ticket,actor.id(),false));
  }

  @Transactional(readOnly=true)
  public Page<Ticket> list(Long owner, Status status, int page, int size) {
    validatePage(page,size);
    return repository.tickets(owner,status,page,size);
  }

  @Transactional(readOnly=true)
  public Ticket detail(long id, Long owner) { return SupportRepository.view(ticket(id,owner,false)); }

  @Transactional(readOnly=true)
  public Page<AdminTicket> queue(Status status,int page,int size) {
    validatePage(page,size);
    return repository.queue(status,page,size);
  }

  @Transactional(readOnly=true)
  public AdminTicket adminDetail(long id) { return SupportRepository.adminView(ticket(id,null,false)); }

  @Transactional(readOnly=true)
  public List<Assignee> assignees() { return repository.assignees(); }

  @Transactional
  public AdminTicket assign(AccountPrincipal actor,long id,Assignment input) {
    accounts.requireAdminForWrite(actor);
    TicketState row = ticket(id,null,true);
    long target = input.adminId() == null ? actor.id() : input.adminId();
    Long previous = assigned(row);
    if (Objects.equals(previous,target)) return SupportRepository.adminView(row);
    if (!Objects.equals(previous,input.expectedAdminId()))
      throw new ApiException(409,"TICKET_ASSIGNMENT_CHANGED","Người phụ trách đã thay đổi. Hãy tải lại phiếu.");
    if ("CLOSED".equals(row.status()))
      throw new ApiException(409,"TICKET_CLOSED","Phiếu đã đóng.");
    if (target != actor.id() && !repository.activeAdmin(target))
      throw new ApiException(422,"ASSIGNEE_UNAVAILABLE","Chọn một quản trị viên đang hoạt động.");
    repository.assign(id,target);
    audit.record(actor.id(),"SUPPORT_ASSIGNED","SUPPORT_TICKET",id);
    return SupportRepository.adminView(ticket(id,null,false));
  }

  @Transactional(readOnly=true)
  public Page<Message> messages(long id, Long owner, int page, int size) {
    validatePage(page,size);
    ticket(id,owner,false);
    return repository.messages(id,page,size);
  }

  @Transactional
  public void reply(AccountPrincipal actor, long id, Reply input, boolean admin) {
    if (admin) accounts.requireAdminForWrite(actor);
    else lockLearner(actor);
    TicketState ticket = ticket(id,admin ? null : actor.id(),true);
    if (admin) requireAssignee(actor,ticket);
    String body = redact(input.body().strip());
    String key = input.clientRequestId().toString();
    var prior = repository.priorMessage(id,actor.id(),key);
    if (prior.isPresent()) {
      if (!body.equals(prior.get().body())) throw reused();
      return;
    }
    if ("CLOSED".equals(ticket.status()))
      throw new ApiException(409,"TICKET_CLOSED","Phiếu đã đóng.");
    long message = repository.addMessage(id,actor.id(),admin ? "ADMIN" : "LEARNER",body,key);
    if (admin) {
      repository.notifyReply(ticket.userId(),id,message);
      audit.record(actor.id(),"SUPPORT_REPLIED","SUPPORT_TICKET",id);
    }
  }

  @Transactional
  public AdminTicket transition(AccountPrincipal actor, long id, Status desired) {
    accounts.requireAdminForWrite(actor);
    var row = ticket(id,null,true);
    requireAssignee(actor,row);
    var current = Status.valueOf(row.status());
    if (current == desired) return SupportRepository.adminView(row);
    if (desired.ordinal() != current.ordinal()+1)
      throw new ApiException(409,"INVALID_TICKET_TRANSITION","Phiếu cần được chuyển theo thứ tự xử lý.");
    repository.transition(id,desired);
    audit.record(actor.id(),"SUPPORT_STATUS_UPDATED","SUPPORT_TICKET",id);
    return SupportRepository.adminView(ticket(id,null,false));
  }

  private static Long assigned(TicketState row) {
    return row.assignedAdminId();
  }

  private static void requireAssignee(AccountPrincipal actor,TicketState row) {
    if (!Objects.equals(assigned(row),actor.id()))
      throw new ApiException(403,"TICKET_ASSIGNEE_REQUIRED","Chỉ người phụ trách được xử lý phiếu.");
  }

  private TicketState ticket(long id, Long owner, boolean lock) {
    return repository.ticket(id,owner,lock)
        .orElseThrow(() -> new ApiException(404,"TICKET_NOT_FOUND","Không tìm thấy phiếu hỗ trợ."));
  }

  private void lockLearner(AccountPrincipal actor) {
    if (!learning.lockAccount(actor.id(),actor.securityVersion()))
      throw new ApiException(401,"SESSION_REVOKED","Vui lòng đăng nhập lại.");
  }

  private static void validatePage(int page, int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 50)
      throw new ApiException(400,"INVALID_PAGINATION","Trang hoặc kích thước trang không hợp lệ.");
  }

  static String redact(String value) {
    value = URL.matcher(value).replaceAll("[đã ẩn]");
    value = SECRET.matcher(value).replaceAll("[đã ẩn]");
    value = EMAIL.matcher(value).replaceAll("[đã ẩn]");
    return NUMBER.matcher(value).replaceAll("[đã ẩn]");
  }

  private static String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
  }

  private static ApiException reused() {
    return new ApiException(409,"REQUEST_ID_REUSED","Mã gửi đã được dùng cho nội dung khác.");
  }
}
