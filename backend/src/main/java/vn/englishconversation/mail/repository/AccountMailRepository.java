package vn.englishconversation.mail.repository;

import org.springframework.stereotype.Repository;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.englishconversation.common.repository.JdbcStore;

/** Persistence only: caller retains transaction, lock ordering, claim/version and cleanup policy. */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class AccountMailRepository {
  private final JdbcStore db;
  private final JdbcTemplate jdbc;
  public AccountMailRepository(JdbcStore db, JdbcTemplate jdbc) { this.db = db; this.jdbc = jdbc; }
  public record Schedule(long id,long version,String timeZone,String localTime,boolean emailReminderEnabled) {}
  public record DeliveryId(long id) {}
  public record MailMessage(String email,String encryptedPayload,String dailyGoalMinutes,String localTime,String timeZone) {}
  public java.util.List<Schedule> activeSchedules(long after) {
    return db.rows("SELECT s.* FROM practice_schedules s JOIN users u ON u.id=s.user_id WHERE s.id>? AND"
                  + " s.enabled=TRUE AND u.status='ACTIVE' AND"
                  + " u.deletion_requested_at IS NULL ORDER BY"
                  + " s.id LIMIT 200", after).stream().map(row -> new Schedule(row.number("id"), row.number("version"), row.text("time_zone"), row.text("local_time"), row.flag("email_reminder_enabled"))).toList();
  }

  public long scheduledDayCount(long schedule,int day) {
    return db.count("SELECT COUNT(*) FROM practice_schedule_days WHERE schedule_id=? AND"
                      + " day_of_week=?", schedule, day);
  }

  public int queueReminder(java.time.LocalDateTime scheduled,java.time.LocalDateTime expires,java.time.LocalDateTime next,long schedule,long version,java.time.LocalDateTime updatedBefore) {
    return jdbc.update("""
                INSERT IGNORE INTO practice_reminder_deliveries
                  (schedule_id,schedule_version,scheduled_for,expires_at,next_attempt_at,created_at)
                SELECT s.id,s.version,?,?,?,UTC_TIMESTAMP(6)
                FROM practice_schedules s JOIN users u ON u.id=s.user_id
                WHERE s.id=? AND s.version=? AND s.updated_at<=? AND s.enabled
                  AND s.email_reminder_enabled AND u.status='ACTIVE' AND u.deletion_requested_at IS NULL
                """, scheduled, expires, next, schedule, version, updatedBefore);
  }

  public int queueNotification(String key,long schedule,long version,java.time.LocalDateTime updatedBefore) {
    return jdbc.update("""
        INSERT IGNORE INTO notifications(user_id,kind,dedupe_key,title,body,created_at)
        SELECT s.user_id,'REMINDER',?,'Đến giờ học tiếng Anh','Mở Hôm nay để chọn việc bạn muốn luyện.',UTC_TIMESTAMP(6)
        FROM practice_schedules s JOIN users u ON u.id=s.user_id
        WHERE s.id=? AND s.version=? AND s.updated_at<=? AND s.enabled AND u.status='ACTIVE' AND u.deletion_requested_at IS NULL
        """, key, schedule, version, updatedBefore);
  }

  public void expireReminderClaims() {
    db.update("""
        UPDATE practice_reminder_deliveries SET status='UNKNOWN',error_code='DELIVERY_UNKNOWN',lease_until=NULL
        WHERE status='PROCESSING' AND lease_until<UTC_TIMESTAMP(6)
        """);
  }

  public void expireDeliveries(String table) {
    validateTable(table);
    db.update("UPDATE "
            + table
            + " SET status='EXPIRED'"
            + (table.equals("account_mail_outbox") ? ",encrypted_payload=NULL" : "")
            + " WHERE expires_at<=UTC_TIMESTAMP(6) AND status IN ('PENDING','RETRY','PROCESSING')");
  }

  public java.util.List<DeliveryId> dueDeliveries(String table) {
    String reclaim = table.equals("practice_reminder_deliveries") ? "" : " OR (status='PROCESSING' AND lease_until<UTC_TIMESTAMP(6))";
    validateTable(table);
    return db.rows("SELECT id FROM "
                + table
                + " WHERE expires_at>UTC_TIMESTAMP(6) AND attempt_count<3 AND ((status IN"
                + " ('PENDING','RETRY') AND next_attempt_at<=UTC_TIMESTAMP(6))"
                  + reclaim + ") ORDER BY id LIMIT 50").stream().map(row -> new DeliveryId(row.number("id"))).toList();
  }

  public int claimDelivery(String table, String claim,long id) {
    String reclaim = table.equals("practice_reminder_deliveries") ? "" : " OR (status='PROCESSING' AND lease_until<UTC_TIMESTAMP(6))";
    validateTable(table);
    return jdbc.update("UPDATE "
                  + table
                  + " SET status='PROCESSING',claim_token=?,lease_until=UTC_TIMESTAMP(6)+INTERVAL 2"
                  + " MINUTE,attempt_count=attempt_count+1 WHERE id=? AND"
                  + " expires_at>UTC_TIMESTAMP(6) AND attempt_count<3 AND ((status IN"
                  + " ('PENDING','RETRY') AND next_attempt_at<=UTC_TIMESTAMP(6))"
                  + reclaim + ")", claim, id);
  }

  public int retryDelivery(String table, long id,String claim) {
    validateTable(table);
    return jdbc.update("UPDATE "
                + table
                + " SET status=IF(attempt_count>=3,'FAILED','RETRY'),error_code='MAIL_SEND_FAILED',next_attempt_at=TIMESTAMPADD(MINUTE,IF(attempt_count=1,1,5),UTC_TIMESTAMP(6)),lease_until=NULL"
                + (table.equals("account_mail_outbox")
                    ? ",encrypted_payload=IF(attempt_count>=3,NULL,encrypted_payload)"
                    : "")
                + " WHERE id=? AND claim_token=? AND status='PROCESSING'", id, claim);
  }

  public void failExhaustedClaims(String table) {
    validateTable(table);
    db.update("UPDATE "
            + table
            + " SET status='FAILED',error_code='DELIVERY_UNKNOWN'"
            + (table.equals("account_mail_outbox") ? ",encrypted_payload=NULL" : "")
            + " WHERE status='PROCESSING' AND attempt_count>=3 AND lease_until<UTC_TIMESTAMP(6)");
  }

  public java.util.Optional<MailMessage> eligibleReset(long id,String claim) {
    return db.find("SELECT o.*,u.email FROM account_mail_outbox o JOIN users u ON"
                  + " u.id=o.user_id JOIN account_action_tokens t ON t.id=o.action_token_id WHERE"
                  + " o.id=? AND o.claim_token=? AND o.status='PROCESSING' AND"
                  + " o.expires_at>UTC_TIMESTAMP(6) AND t.expires_at>UTC_TIMESTAMP(6) AND"
                  + " t.consumed_at IS NULL AND t.email=u.email AND u.status='ACTIVE' AND"
                  + " u.deletion_requested_at IS NULL", id, claim).map(row -> new MailMessage(row.text("email"), row.text("encrypted_payload"), null, null, null));
  }

  public java.util.Optional<MailMessage> eligibleReminder(long id,String claim) {
    return db.find("SELECT d.*,u.email,s.local_time,s.time_zone,p.daily_goal_minutes FROM"
                + " practice_reminder_deliveries d JOIN practice_schedules s ON s.id=d.schedule_id"
                + " JOIN users u ON u.id=s.user_id LEFT JOIN learner_profiles p ON p.user_id=u.id"
                + " WHERE d.id=? AND d.claim_token=? AND d.status='PROCESSING' AND"
                + " d.expires_at>UTC_TIMESTAMP(6) AND d.schedule_version=s.version AND"
                + " s.enabled=TRUE AND s.email_reminder_enabled=TRUE AND u.status='ACTIVE' AND"
                + " u.deletion_requested_at IS NULL", id, claim).map(row -> new MailMessage(row.text("email"), null, row.text("daily_goal_minutes"), row.text("local_time"), row.text("time_zone")));
  }

  public int finishDelivery(String table, String status,String sentStatus,String error,long id,String claim) {
    validateTable(table);
    return jdbc.update("UPDATE "
            + table
            + " SET status=?,sent_at=IF(?='SENT',UTC_TIMESTAMP(6),sent_at),error_code=?,lease_until=NULL"
            + (table.equals("account_mail_outbox") ? ",encrypted_payload=NULL" : "")
            + " WHERE id=? AND claim_token=? AND status='PROCESSING'", status, sentStatus, error, id, claim);
  }

  public void clearExpiredPayloads() {
    db.update("UPDATE account_mail_outbox SET"
            + " encrypted_payload=NULL,status=IF(status='SENT','SENT','EXPIRED') WHERE"
            + " expires_at<=UTC_TIMESTAMP(6) AND encrypted_payload IS NOT NULL");
  }

  public void purgeTokens() {
    db.update("DELETE FROM account_action_tokens WHERE (consumed_at IS NOT NULL OR"
            + " expires_at<=UTC_TIMESTAMP(6)) AND created_at<UTC_TIMESTAMP(6)-INTERVAL 30 DAY");
  }

  public void purgeResetMail() {
    db.update("DELETE FROM account_mail_outbox WHERE status IN ('SENT','FAILED','CANCELLED','EXPIRED','UNKNOWN')"
            + " AND created_at<UTC_TIMESTAMP(6)-INTERVAL 30 DAY");
  }

  public void purgeReminders() {
    db.update("DELETE FROM practice_reminder_deliveries WHERE status IN"
            + " ('SENT','FAILED','CANCELLED','EXPIRED','UNKNOWN') AND created_at<UTC_TIMESTAMP(6)-INTERVAL 30"
            + " DAY");
  }

  public void purgeRateBuckets() {
    db.update("DELETE FROM account_rate_limits WHERE window_start<UTC_DATE() AND last_request_at<UTC_TIMESTAMP(6)-INTERVAL 2 HOUR");
  }

  private static void validateTable(String table) {
    if (!java.util.Set.of("account_mail_outbox", "practice_reminder_deliveries").contains(table))
      throw new IllegalArgumentException("Unsupported delivery table");
  }
}
