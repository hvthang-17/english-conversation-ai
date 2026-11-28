package vn.englishconversation.admin.repository;

import static vn.englishconversation.admin.dto.AdminAnnouncement.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.admin.dto.AdminPage;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.ADMIN)
public class AdminAnnouncementStore {
  private static final String RECIPIENTS = """
      FROM users u LEFT JOIN learner_profiles p ON p.user_id=u.id
      WHERE u.status='ACTIVE' AND u.deletion_requested_at IS NULL AND u.role=?
        AND (? IS NULL OR p.selected_level=?)
      """;
  private static final String VIEW = """
      SELECT a.*,(SELECT COUNT(*) FROM notifications n WHERE n.announcement_id=a.id) AS recipients
      FROM announcements a
      """;
  private final JdbcStore db;

  public AdminAnnouncementStore(JdbcStore db) { this.db = db; }

  public AdminPage<View> list(int page,int size,Instant now) {
    return new AdminPage<>(db.rows(VIEW+" ORDER BY a.id DESC LIMIT ? OFFSET ?",size,(long)page*size)
        .stream().map(row -> view(row,now)).toList(),page,size,db.count("SELECT COUNT(*) FROM announcements"));
  }

  private Optional<Row> previousRows(long actor,String key) {
    return db.find("SELECT id,request_fingerprint FROM announcements WHERE created_by=? AND client_request_id=?",actor,key);
  }

  private Optional<Row> lockRows(long id) {
    return db.find("SELECT * FROM announcements WHERE id=? FOR UPDATE",id);
  }

  public View get(long id,Instant now) {
    return db.find(VIEW+" WHERE a.id=?",id).map(row -> view(row,now)).orElseThrow();
  }

  public long create(long actor,Draft draft,String key,String fingerprint) {
    return db.insert("""
        INSERT INTO announcements(created_by,title,body,audience_role,audience_level,expires_at,
          created_at,client_request_id,request_fingerprint)
        VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(6),?,?)
        """,actor,draft.title(),draft.body(),draft.audienceRole().name(),level(draft),utc(draft.expiresAt()),key,fingerprint);
  }

  public void update(long id,Draft draft) {
    db.update("""
        UPDATE announcements SET title=?,body=?,audience_role=?,audience_level=?,expires_at=?,version=version+1 WHERE id=?
        """,draft.title(),draft.body(),draft.audienceRole().name(),level(draft),utc(draft.expiresAt()),id);
  }

  public long estimatedRecipients(Draft draft) {
    return db.count("SELECT COUNT(*) "+RECIPIENTS,draft.audienceRole().name(),level(draft),level(draft));
  }

  public void publish(long id,Draft draft,Instant at) {
    db.update("UPDATE announcements SET status='PUBLISHED',published_at=?,version=version+1 WHERE id=?",utc(at),id);
    db.update("""
        INSERT INTO notifications(user_id,announcement_id,kind,dedupe_key,title,body,created_at)
        SELECT u.id,?,'ANNOUNCEMENT',?,?,?,?
        """+RECIPIENTS,id,"announcement:"+id,draft.title(),draft.body(),utc(at),draft.audienceRole().name(),level(draft),level(draft));
  }

  public void archive(long id) {
    db.update("UPDATE announcements SET status='ARCHIVED',version=version+1 WHERE id=?",id);
  }

  private static Draft draft(Row row) {
    String role=row.text("audience_role"), level=row.text("audience_level");
    return new Draft(row.text("title"),row.text("body"),role==null ? null : Audience.valueOf(role),
        level==null ? null : Level.valueOf(level),instant(row,"expires_at"));
  }

  private static View view(Row row,Instant now) {
    Draft draft=draft(row);
    String status=row.text("status");
    if ("PUBLISHED".equals(status) && draft.expiresAt()!=null && !draft.expiresAt().isAfter(now)) status="EXPIRED";
    return new View(row.text("id"),draft,status,row.number("version"),row.number("recipients"),instant(row,"published_at"));
  }

  private static String level(Draft draft) { return draft.audienceLevel()==null ? null : draft.audienceLevel().name(); }
  private static LocalDateTime utc(Instant value) { return value==null ? null : LocalDateTime.ofInstant(value,ZoneOffset.UTC); }
  private static Instant instant(Row row,String field) {
    LocalDateTime value=(LocalDateTime)row.data().get(field);
    return value==null ? null : value.toInstant(ZoneOffset.UTC);
  }

  public record Previous(long id,String requestFingerprint) {}
  private static Previous mapPrevious(Row row) { return new Previous(row.number("id"),row.text("request_fingerprint")); }
  public record LockedAnnouncement(String status,long version,Draft draft) {}
  private static LockedAnnouncement mapLockedAnnouncement(Row row) { return new LockedAnnouncement(row.text("status"),row.number("version"),draft(row)); }
  public java.util.Optional<Previous> previous(long actor,String key) { return previousRows(actor,key).map(AdminAnnouncementStore::mapPrevious); }
  public java.util.Optional<LockedAnnouncement> lock(long id) { return lockRows(id).map(AdminAnnouncementStore::mapLockedAnnouncement); }
}
