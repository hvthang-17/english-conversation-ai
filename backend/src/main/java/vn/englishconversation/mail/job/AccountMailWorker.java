package vn.englishconversation.mail.job;

import vn.englishconversation.mail.provider.MailGateway;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.englishconversation.auth.security.TokenCipher;
import vn.englishconversation.auth.service.AccountLifecycleService;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;
import vn.englishconversation.profile.service.PracticeScheduleService;

@Component
@Profile("!bootstrap")
public class AccountMailWorker {
  private final JdbcStore db;
  private final JdbcTemplate jdbc;
  private final MailGateway mail;
  private final TokenCipher cipher;
  private final AccountLifecycleService lifecycle;
  private final TransactionTemplate tx;
  private final boolean remindersEnabled;
  private final boolean inAppReminders;

  public AccountMailWorker(
      JdbcStore db,
      JdbcTemplate jdbc,
      MailGateway mail,
      TokenCipher cipher,
      AccountLifecycleService lifecycle,
      PlatformTransactionManager manager,
      org.springframework.core.env.Environment environment,
      @Value("${app.mail.reminders-enabled:true}") boolean remindersEnabled) {
    this.db = db;
    this.jdbc = jdbc;
    this.mail = mail;
    this.cipher = cipher;
    this.lifecycle = lifecycle;
    tx = new TransactionTemplate(manager);
    this.remindersEnabled = remindersEnabled;
    this.inAppReminders = environment.matchesProfiles("g6");
  }

  @Scheduled(fixedDelayString = "${app.mail.poll-ms:60000}")
  public void tick() {
    boolean smtp = mail.available();
    if (lifecycle.mailAvailable()) sendBatch("account_mail_outbox");
    if (inAppReminders || (smtp && remindersEnabled)) generate(Instant.now());
    if (smtp && remindersEnabled) sendBatch("practice_reminder_deliveries");
  }

  public void generate(Instant now) {
    // Bounded pages; resume through all schedules without retaining their content.
    long after = 0;
    while (true) {
      var rows =
          db.rows(
              "SELECT s.* FROM practice_schedules s JOIN users u ON u.id=s.user_id WHERE s.id>? AND"
                  + " s.enabled=TRUE AND u.status='ACTIVE' AND"
                  + " u.deletion_requested_at IS NULL ORDER BY"
                  + " s.id LIMIT 200",
              after);
      if (rows.isEmpty()) break;
      for (var row : rows) {
        after = row.number("id");
        var zone = ZoneId.of(row.text("time_zone"));
        var date = now.atZone(zone).toLocalDate();
        // Include yesterday for a midnight-crossing retry window.
        for (var d : List.of(date.minusDays(1), date)) {
          if (db.count(
                  "SELECT COUNT(*) FROM practice_schedule_days WHERE schedule_id=? AND"
                      + " day_of_week=?",
                  row.number("id"),
                  d.getDayOfWeek().getValue())
              == 0) continue;
          Instant at =
              PracticeScheduleService.occurrence(d, LocalTime.parse(row.text("local_time")), zone);
          if (at == null || at.isAfter(now) || !at.plusSeconds(1800).isAfter(now)) continue;
          // Do not generate an occurrence predating the current revision.
          if (db.count(
                  "SELECT COUNT(*) FROM practice_schedules WHERE id=? AND version=? AND"
                      + " updated_at<=?",
                  row.number("id"),
                  row.number("version"),
                  Timestamp.from(at))
              == 0) continue;
          if (inAppReminders) inAppReminder(row, at);
          if (!row.flag("email_reminder_enabled")) continue;
          if (inAppReminders && (!remindersEnabled || !mail.available())) continue;
          jdbc.update(
              "INSERT IGNORE INTO"
                  + " practice_reminder_deliveries(schedule_id,schedule_version,scheduled_for,expires_at,next_attempt_at,created_at)"
                  + " VALUES (?,?,?,?,?,UTC_TIMESTAMP(6))",
              row.number("id"),
              row.number("version"),
              Timestamp.from(at),
              Timestamp.from(at.plusSeconds(1800)),
              Timestamp.from(at));
        }
      }
    }
  }

  private void inAppReminder(Row schedule, Instant at) {
    jdbc.update("""
        INSERT IGNORE INTO notifications(user_id,kind,dedupe_key,title,body,created_at)
        SELECT s.user_id,'REMINDER',?,'Đến giờ học tiếng Anh','Mở Hôm nay để chọn việc bạn muốn luyện.',UTC_TIMESTAMP(6)
        FROM practice_schedules s JOIN users u ON u.id=s.user_id
        WHERE s.id=? AND s.version=? AND s.enabled AND u.status='ACTIVE' AND u.deletion_requested_at IS NULL
        """, "reminder:" + schedule.number("id") + ":" + at, schedule.number("id"), schedule.number("version"));
  }

  private static boolean connectionFailedBeforeSend(Throwable error) {
    if (error instanceof MailSendException sendFailure) {
      // JavaMailSender can put the transport error in its per-message result.
      Exception[] messageFailures = sendFailure.getMessageExceptions();
      if (messageFailures.length > 0) {
        return messageFailures.length == 1 && hasConnectionFailure(messageFailures[0]);
      }
    }
    return hasConnectionFailure(error);
  }

  private static boolean hasConnectionFailure(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause())
      if (cause instanceof java.net.ConnectException || cause instanceof java.net.UnknownHostException) return true;
    return false;
  }

  private void sendBatch(String table) {
    boolean reminder = table.equals("practice_reminder_deliveries");
    if (reminder) db.update("""
        UPDATE practice_reminder_deliveries SET status='UNKNOWN',error_code='DELIVERY_UNKNOWN',lease_until=NULL
        WHERE status='PROCESSING' AND lease_until<UTC_TIMESTAMP(6)
        """);
    String reclaim = reminder ? "" : " OR (status='PROCESSING' AND lease_until<UTC_TIMESTAMP(6))";

    db.update(
        "UPDATE "
            + table
            + " SET status='EXPIRED'"
            + (table.equals("account_mail_outbox") ? ",encrypted_payload=NULL" : "")
            + " WHERE expires_at<=UTC_TIMESTAMP(6) AND status IN ('PENDING','RETRY','PROCESSING')");
    var rows =
        db.rows(
            "SELECT id FROM "
                + table
                + " WHERE expires_at>UTC_TIMESTAMP(6) AND attempt_count<3 AND ((status IN"
                + " ('PENDING','RETRY') AND next_attempt_at<=UTC_TIMESTAMP(6))"
                  + reclaim + ") ORDER BY id LIMIT 50");
    for (var row : rows) {
      String claim = UUID.randomUUID().toString();
      long id = row.number("id");
      int claimed =
          jdbc.update(
              "UPDATE "
                  + table
                  + " SET status='PROCESSING',claim_token=?,lease_until=UTC_TIMESTAMP(6)+INTERVAL 2"
                  + " MINUTE,attempt_count=attempt_count+1 WHERE id=? AND"
                  + " expires_at>UTC_TIMESTAMP(6) AND attempt_count<3 AND ((status IN"
                  + " ('PENDING','RETRY') AND next_attempt_at<=UTC_TIMESTAMP(6))"
                  + reclaim + ")",
              claim,
              id);
      if (claimed == 0) continue;
      try {
        Row message = tx.execute(s -> eligible(table, id, claim));
        if (message == null) {
          finish(table, id, claim, "CANCELLED", null);
          continue;
        }
        // The check/SMTP boundary is intentionally outside a transaction. A send already
        // in flight cannot be recalled by a concurrent schedule change or deletion.
        if (table.equals("account_mail_outbox")) {
          String url =
              lifecycle.publicUrl()
                  + "/#/reset-password?token="
                  + cipher.decrypt(message.text("encrypted_payload"));
          mail.send(
              message.text("email"),
              "Khôi phục mật khẩu",
              "Đặt lại mật khẩu bằng liên kết một lần:\n"
                  + url
                  + "\nNếu bạn không yêu cầu, hãy bỏ qua email này.");
        } else {
          String minutes = message.text("daily_goal_minutes");
          mail.send(
              message.text("email"),
              "Đến giờ luyện tiếng Anh rồi!",
              "Bạn đã lên lịch luyện lúc "
                  + message.text("local_time")
                  + " ("
                  + message.text("time_zone")
                  + ").\n"
                  + (minutes == null ? "" : "Mục tiêu: " + minutes + " phút/ngày.\n")
                  + "Bắt đầu: "
                  + lifecycle.publicUrl()
                  + "/#/scenarios\nĐổi lịch hoặc tắt nhắc học: "
                  + lifecycle.publicUrl()
                  + "/#/profile");
        }
        finish(table, id, claim, "SENT", null);
      } catch (Exception failure) {
        if (reminder && failure instanceof org.springframework.mail.MailAuthenticationException) {
          finish(table,id,claim,"FAILED","MAIL_AUTH_FAILED");
          continue;
        }
        if (reminder && !connectionFailedBeforeSend(failure)) {
          finish(table,id,claim,"UNKNOWN","DELIVERY_UNKNOWN");
          continue;
        }
        jdbc.update(
            "UPDATE "
                + table
                + " SET status=IF(attempt_count>=3,'FAILED','RETRY'),error_code='MAIL_SEND_FAILED',next_attempt_at=TIMESTAMPADD(MINUTE,IF(attempt_count=1,1,5),UTC_TIMESTAMP(6)),lease_until=NULL"
                + (table.equals("account_mail_outbox")
                    ? ",encrypted_payload=IF(attempt_count>=3,NULL,encrypted_payload)"
                    : "")
                + " WHERE id=? AND claim_token=? AND status='PROCESSING'",
            id,
            claim);
      }
    }
    // A crash on the final attempt must not leave a PROCESSING row forever.
    db.update(
        "UPDATE "
            + table
            + " SET status='FAILED',error_code='DELIVERY_UNKNOWN'"
            + (table.equals("account_mail_outbox") ? ",encrypted_payload=NULL" : "")
            + " WHERE status='PROCESSING' AND attempt_count>=3 AND lease_until<UTC_TIMESTAMP(6)");
  }

  private Row eligible(String table, long id, String claim) {
    if (table.equals("account_mail_outbox"))
      return db.find(
              "SELECT o.*,u.email FROM account_mail_outbox o JOIN users u ON"
                  + " u.id=o.user_id JOIN account_action_tokens t ON t.id=o.action_token_id WHERE"
                  + " o.id=? AND o.claim_token=? AND o.status='PROCESSING' AND"
                  + " o.expires_at>UTC_TIMESTAMP(6) AND t.expires_at>UTC_TIMESTAMP(6) AND"
                  + " t.consumed_at IS NULL AND t.email=u.email AND u.status='ACTIVE' AND"
                  + " u.deletion_requested_at IS NULL",
              id,
              claim)
          .orElse(null);
    return db.find(
            "SELECT d.*,u.email,s.local_time,s.time_zone,p.daily_goal_minutes FROM"
                + " practice_reminder_deliveries d JOIN practice_schedules s ON s.id=d.schedule_id"
                + " JOIN users u ON u.id=s.user_id LEFT JOIN learner_profiles p ON p.user_id=u.id"
                + " WHERE d.id=? AND d.claim_token=? AND d.status='PROCESSING' AND"
                + " d.expires_at>UTC_TIMESTAMP(6) AND d.schedule_version=s.version AND"
                + " s.enabled=TRUE AND u.status='ACTIVE' AND"
                + " u.deletion_requested_at IS NULL",
            id,
            claim)
        .orElse(null);
  }

  private void finish(String table, long id, String claim, String status, String error) {
    jdbc.update(
        "UPDATE "
            + table
            + " SET status=?,sent_at=IF(?='SENT',UTC_TIMESTAMP(6),sent_at),error_code=?,lease_until=NULL"
            + (table.equals("account_mail_outbox") ? ",encrypted_payload=NULL" : "")
            + " WHERE id=? AND claim_token=? AND status='PROCESSING'",
        status,
        status,
        error,
        id,
        claim);
  }

  @Scheduled(fixedDelayString = "${app.account.cleanup-ms:300000}")
  public void cleanup() {
    db.update(
        "UPDATE account_mail_outbox SET"
            + " encrypted_payload=NULL,status=IF(status='SENT','SENT','EXPIRED') WHERE"
            + " expires_at<=UTC_TIMESTAMP(6) AND encrypted_payload IS NOT NULL");
    db.update(
        "DELETE FROM account_action_tokens WHERE (consumed_at IS NOT NULL OR"
            + " expires_at<=UTC_TIMESTAMP(6)) AND created_at<UTC_TIMESTAMP(6)-INTERVAL 30 DAY");
    db.update(
        "DELETE FROM account_mail_outbox WHERE status IN ('SENT','FAILED','CANCELLED','EXPIRED','UNKNOWN')"
            + " AND created_at<UTC_TIMESTAMP(6)-INTERVAL 30 DAY");
    db.update(
        "DELETE FROM practice_reminder_deliveries WHERE status IN"
            + " ('SENT','FAILED','CANCELLED','EXPIRED','UNKNOWN') AND created_at<UTC_TIMESTAMP(6)-INTERVAL 30"
            + " DAY");
    db.update(
        "DELETE FROM account_rate_limits WHERE window_start<UTC_DATE() AND last_request_at<UTC_TIMESTAMP(6)-INTERVAL 2 HOUR");
  }
}
