package vn.englishconversation.support.repository;

import static vn.englishconversation.support.dto.SupportData.*;

import java.util.Optional;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.SUPPORT)
public class SupportRepository {
  private static final String TICKETS = """
      SELECT t.*, DATE_FORMAT(t.created_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS created,
        DATE_FORMAT(t.updated_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS updated FROM support_tickets t
      """;
  private final JdbcStore db;

  public SupportRepository(JdbcStore db) { this.db = db; }

  public Optional<String> source(long owner, Source source, boolean lock) {
    String sql = switch (source.type()) {
      case LESSON -> """
          SELECT CONCAT(l.title, CHAR(10), l.summary) AS excerpt FROM lessons l JOIN topics t ON t.id=l.topic_id
          WHERE l.id=? AND l.status='ACTIVE' AND t.is_active
          """;
      case AI_MESSAGE -> """
          SELECT m.content AS excerpt FROM messages m JOIN practice_sessions p ON p.id=m.session_id
          WHERE m.id=? AND p.user_id=? AND m.sender='ASSISTANT'
          """;
      case FEEDBACK -> """
          SELECT f.summary AS excerpt FROM session_feedback f JOIN practice_sessions p ON p.id=f.session_id
          WHERE f.session_id=? AND p.user_id=? AND f.status='SUCCEEDED'
          """;
    };
    sql += lock ? " FOR SHARE" : "";
    return (source.type() == SourceType.LESSON ? db.find(sql, source.id()) : db.find(sql, source.id(), owner))
        .map(row -> row.text("excerpt"));
  }

  private Optional<Row> priorRows(long owner, String key) {
    return db.find(TICKETS + " WHERE t.user_id=? AND t.client_request_id=?", owner, key);
  }

  private Optional<Row> ticketRows(long id, Long owner, boolean lock) {
    String query = TICKETS + " WHERE t.id=?" + (owner == null ? "" : " AND t.user_id=?") + (lock ? " FOR UPDATE" : "");
    return owner == null ? db.find(query,id) : db.find(query,id,owner);
  }

  public Page<Ticket> tickets(Long owner, Status status, int page, int size) {
    return tickets(owner,status,page,size,SupportRepository::view);
  }

  public Page<AdminTicket> queue(Status status, int page, int size) {
    return tickets(null,status,page,size,SupportRepository::adminView);
  }

  private <T> Page<T> tickets(Long owner, Status status, int page, int size, java.util.function.Function<Row,T> mapper) {
    String filter = owner != null ? " WHERE t.user_id=?" : status == null ? "" : " WHERE t.status=?";
    Object[] args = owner != null ? new Object[]{owner} : status == null ? new Object[0] : new Object[]{status.name()};
    long total = db.count("SELECT COUNT(*) FROM support_tickets t" + filter, args);
    Object[] paging = java.util.Arrays.copyOf(args,args.length+2);
    paging[args.length] = size;
    paging[args.length+1] = (long) page*size;
    var items = db.rows(TICKETS + filter + " ORDER BY t.updated_at DESC,t.id DESC LIMIT ? OFFSET ?", paging)
        .stream().map(mapper).toList();
    return new Page<>(items,page,size,total);
  }

  public long create(long owner, Create input, String title, String excerpt, String fingerprint) {
    Source source = input.source();
    return db.insert("""
        INSERT INTO support_tickets
        (user_id,kind,title,lesson_id,message_id,feedback_id,redacted_excerpt,client_request_id,request_fingerprint,created_at,updated_at)
        VALUES (?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """, owner,input.kind().name(),title,sourceId(source,SourceType.LESSON),
        sourceId(source,SourceType.AI_MESSAGE),feedbackId(source),excerpt,
        input.clientRequestId().toString(),fingerprint);
  }

  private static Long sourceId(Source source, SourceType type) {
    return source != null && source.type() == type ? source.id() : null;
  }

  private Long feedbackId(Source source) {
    return sourceId(source,SourceType.FEEDBACK) == null ? null :
        db.find("SELECT id FROM session_feedback WHERE session_id=? FOR SHARE",source.id()).orElseThrow().number("id");
  }

  private Optional<Row> priorMessageRows(long ticket, long author, String key) {
    return db.find("SELECT id,body FROM support_ticket_messages WHERE ticket_id=? AND author_id=? AND client_request_id=?",
        ticket,author,key);
  }

  public long addMessage(long ticket, long author, String role, String body, String key) {
    long id = db.insert("""
        INSERT INTO support_ticket_messages(ticket_id,author_id,author_role,body,client_request_id,created_at)
        VALUES (?,?,?,?,?,UTC_TIMESTAMP(6))
        """,ticket,author,role,body,key);
    db.update("UPDATE support_tickets SET updated_at=UTC_TIMESTAMP(6) WHERE id=?",ticket);
    return id;
  }

  public Page<Message> messages(long ticket, int page, int size) {
    long total = db.count("SELECT COUNT(*) FROM support_ticket_messages WHERE ticket_id=?",ticket);
    var items = db.rows("""
        SELECT id,author_role,body,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS created
        FROM support_ticket_messages WHERE ticket_id=? ORDER BY id LIMIT ? OFFSET ?
        """,ticket,size,(long)page*size).stream()
        .map(r -> new Message(r.text("id"),r.text("author_role"),r.text("body"),r.text("created"))).toList();
    return new Page<>(items,page,size,total);
  }

  public java.util.List<Assignee> assignees() {
    return db.rows("""
        SELECT id,email FROM users WHERE role='ADMIN' AND status='ACTIVE' AND deletion_requested_at IS NULL
        ORDER BY email,id
        """).stream().map(row -> new Assignee(row.text("id"),row.text("email"))).toList();
  }

  public boolean activeAdmin(long admin) {
    return db.find("""
        SELECT id FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE' AND deletion_requested_at IS NULL FOR SHARE
        """,admin).isPresent();
  }

  public void assign(long ticket, long admin) {
    db.update("UPDATE support_tickets SET assigned_admin_id=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",admin,ticket);
  }

  public void transition(long ticket, Status status) {
    db.update("""
        UPDATE support_tickets SET status=?,updated_at=UTC_TIMESTAMP(6),
          closed_at=IF(?='CLOSED',UTC_TIMESTAMP(6),NULL) WHERE id=?
        """,status.name(),status.name(),ticket);
  }

  public void notifyReply(long owner, long ticket, long message) {
    db.update("""
        INSERT INTO notifications(user_id,support_ticket_id,kind,dedupe_key,title,body,created_at)
        VALUES (?,?,'SUPPORT',?,'Có phản hồi mới từ hỗ trợ','Mở phiếu để xem nội dung trao đổi.',UTC_TIMESTAMP(6))
        """,owner,ticket,"support-reply:"+message);
  }

  private static Ticket view(Row row) {
    return new Ticket(row.text("id"),Kind.valueOf(row.text("kind")),Status.valueOf(row.text("status")),
        row.text("title"),row.text("redacted_excerpt"),row.text("source_removed_at")!=null,
        row.text("created"),row.text("updated"));
  }

  private static AdminTicket adminView(Row row) {
    return new AdminTicket(view(row),row.text("assigned_admin_id"));
  }

  public record TicketState(String id,long userId,String kind,String status,String title,String redactedExcerpt,String sourceRemovedAt,String created,String updated,Long assignedAdminId,String requestFingerprint) {}
  private static TicketState mapTicketState(Row row) { return new TicketState(row.text("id"),row.number("user_id"),row.text("kind"),row.text("status"),row.text("title"),row.text("redacted_excerpt"),row.text("source_removed_at"),row.text("created"),row.text("updated"),(row.data().get("assigned_admin_id")==null ? null : ((Number)row.data().get("assigned_admin_id")).longValue()),row.text("request_fingerprint")); }
  public record PriorMessage(String body) {}
  private static PriorMessage mapPriorMessage(Row row) { return new PriorMessage(row.text("body")); }
  public java.util.Optional<TicketState> prior(long owner,String key) { return priorRows(owner,key).map(SupportRepository::mapTicketState); }
  public java.util.Optional<TicketState> ticket(long id,Long owner,boolean lock) { return ticketRows(id,owner,lock).map(SupportRepository::mapTicketState); }
  public java.util.Optional<PriorMessage> priorMessage(long ticket,long author,String key) { return priorMessageRows(ticket,author,key).map(SupportRepository::mapPriorMessage); }

  public static Ticket view(TicketState row) {
    return new Ticket(row.id(),Kind.valueOf(row.kind()),Status.valueOf(row.status()),row.title(),
      row.redactedExcerpt(),row.sourceRemovedAt()!=null,row.created(),row.updated());
  }
  public static AdminTicket adminView(TicketState row) { return new AdminTicket(view(row), row.assignedAdminId()==null ? null : row.assignedAdminId().toString()); }
}
