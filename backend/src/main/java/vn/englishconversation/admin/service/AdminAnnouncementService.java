package vn.englishconversation.admin.service;

import static vn.englishconversation.admin.dto.AdminAnnouncement.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.admin.dto.AdminPage;
import vn.englishconversation.admin.repository.AdminAnnouncementStore;
import vn.englishconversation.admin.repository.AdminAudit;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.admin.repository.AdminAnnouncementStore.LockedAnnouncement;

@Service
@FeatureEnabled(FeatureEnabled.Feature.ADMIN)
public class AdminAnnouncementService {
  private final AdminAnnouncementStore store;
  private final AccountService accounts;
  private final AdminAudit audit;

  public AdminAnnouncementService(AdminAnnouncementStore store,AccountService accounts,AdminAudit audit) {
    this.store=store;
    this.accounts=accounts;
    this.audit=audit;
  }

  @Transactional(readOnly=true)
  public AdminPage<View> list(int page,int size) {
    if (page<0 || page>100000 || size<1 || size>50)
      throw new ApiException(400,"INVALID_PAGE","Trang hoặc kích thước trang không hợp lệ.");
    return store.list(page,size,Instant.now());
  }

  @Transactional(readOnly=true)
  public Preview preview(Draft input) {
    Draft draft=normalize(input);
    return new Preview(draft,store.estimatedRecipients(draft));
  }

  @Transactional
  public View create(AccountPrincipal actor,Save input) {
    if (input.clientRequestId()==null)
      throw new ApiException(400,"REQUEST_ID_REQUIRED","Thiếu mã tạo bản nháp.");
    accounts.requireAdminForWrite(actor);
    Draft draft=normalize(input.draft());
    String key=input.clientRequestId().toString(), fingerprint=fingerprint(draft);
    var previous=store.previous(actor.id(),key);
    if (previous.isPresent()) {
      if (!fingerprint.equals(previous.get().requestFingerprint()))
        throw new ApiException(409,"REQUEST_ID_REUSED","Mã yêu cầu đã được dùng cho nội dung khác.");
      return store.get(previous.get().id(),Instant.now());
    }
    long id=store.create(actor.id(),draft,key,fingerprint);
    audit.record(actor.id(),"ANNOUNCEMENT_CREATED","ANNOUNCEMENT",id);
    return store.get(id,Instant.now());
  }

  @Transactional
  public View update(AccountPrincipal actor,long id,Save input) {
    accounts.requireAdminForWrite(actor);
    LockedAnnouncement row=lock(id);
    requireDraft(row,input.version());
    Draft draft=normalize(input.draft());
    if (!draft.equals(row.draft())) {
      store.update(id,draft);
      audit.record(actor.id(),"ANNOUNCEMENT_UPDATED","ANNOUNCEMENT",id);
    }
    return store.get(id,Instant.now());
  }

  @Transactional
  public View publish(AccountPrincipal actor,long id,long version) {
    accounts.requireAdminForWrite(actor);
    LockedAnnouncement row=lock(id);
    Instant now=Instant.now().truncatedTo(ChronoUnit.MICROS);
    if ("PUBLISHED".equals(row.status())) return store.get(id,now);
    requireDraft(row,version);
    Draft draft=normalize(row.draft());
    if (draft.expiresAt()!=null && !draft.expiresAt().isAfter(now))
      throw new ApiException(422,"ANNOUNCEMENT_EXPIRED","Hạn hiển thị phải sau thời điểm công bố.");
    store.publish(id,draft,now);
    audit.record(actor.id(),"ANNOUNCEMENT_PUBLISHED","ANNOUNCEMENT",id);
    return store.get(id,now);
  }

  @Transactional
  public View archive(AccountPrincipal actor,long id) {
    accounts.requireAdminForWrite(actor);
    String status=lock(id).status();
    if ("PUBLISHED".equals(status)) {
      store.archive(id);
      audit.record(actor.id(),"ANNOUNCEMENT_ARCHIVED","ANNOUNCEMENT",id);
    } else if (!"ARCHIVED".equals(status)) {
      throw new ApiException(409,"ANNOUNCEMENT_NOT_PUBLISHED","Thông báo cần được công bố trước khi ẩn.");
    }
    return store.get(id,Instant.now());
  }

  private LockedAnnouncement lock(long id) {
    return store.lock(id).orElseThrow(() -> new ApiException(404,"ANNOUNCEMENT_NOT_FOUND","Không tìm thấy thông báo."));
  }

  private static void requireDraft(LockedAnnouncement row,Long version) {
    if (!"DRAFT".equals(row.status()) || version==null || version!=row.version())
      throw new ApiException(409,"ANNOUNCEMENT_CHANGED","Bản nháp đã thay đổi. Hãy tải lại trước khi tiếp tục.");
  }

  private static Draft normalize(Draft draft) {
    if (draft.audienceRole()==null || draft.audienceRole()==Audience.ADMIN && draft.audienceLevel()!=null)
      throw new ApiException(422,"ANNOUNCEMENT_AUDIENCE_INVALID","Chỉ lọc trình độ cho nhóm học viên.");
    Instant expires=draft.expiresAt()==null ? null : draft.expiresAt().truncatedTo(ChronoUnit.MICROS);
    if (expires!=null && (expires.isBefore(Instant.parse("1970-01-01T00:00:00Z"))
        || expires.isAfter(Instant.parse("9999-12-31T23:59:59Z"))))
      throw new ApiException(422,"ANNOUNCEMENT_EXPIRY_INVALID","Thời hạn hiển thị nằm ngoài phạm vi cho phép.");
    return new Draft(draft.title().strip(),draft.body().strip(),draft.audienceRole(),draft.audienceLevel(),expires);
  }

  private static String fingerprint(Draft draft) {
    try {
      String content=draft.title().length()+":"+draft.title()+draft.body().length()+":"+draft.body()
          +":"+draft.audienceRole()+":"+draft.audienceLevel()+":"+draft.expiresAt();
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
  }
}
