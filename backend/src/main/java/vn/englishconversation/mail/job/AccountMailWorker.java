package vn.englishconversation.mail.job;

import vn.englishconversation.mail.provider.MailGateway;

import java.time.*;
import java.util.*;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.englishconversation.auth.security.TokenCipher;
import vn.englishconversation.auth.service.AccountLifecycleService;
import vn.englishconversation.mail.repository.AccountMailRepository;
import vn.englishconversation.profile.service.PracticeScheduleService;

@Component
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class AccountMailWorker {
  private final AccountMailRepository persistence;
  private final MailGateway mail;
  private final TokenCipher cipher;
  private final AccountLifecycleService lifecycle;
  private final TransactionTemplate tx;
  private final boolean remindersEnabled;
  private final boolean inAppReminders;

  public AccountMailWorker(AccountMailRepository persistence,
            MailGateway mail,
      TokenCipher cipher,
      AccountLifecycleService lifecycle,
      PlatformTransactionManager manager,
      vn.englishconversation.config.features.FeatureSet features,
      @Value("${app.mail.reminders-enabled:false}") boolean remindersEnabled) {
    this.persistence = persistence;
    this.mail = mail;
    this.cipher = cipher;
    this.lifecycle = lifecycle;
    tx = new TransactionTemplate(manager);
    this.remindersEnabled = remindersEnabled;
    this.inAppReminders = features.enabled(vn.englishconversation.config.features.FeatureEnabled.Feature.IN_APP_REMINDERS);
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
          persistence.activeSchedules(after);
      if (rows.isEmpty()) break;
      for (var row : rows) {
        after = row.id();
        var zone = ZoneId.of(row.timeZone());
        var date = now.atZone(zone).toLocalDate();
        // Include yesterday for a midnight-crossing retry window.
        for (var d : List.of(date.minusDays(1), date)) {
          if (persistence.scheduledDayCount(row.id(), d.getDayOfWeek().getValue())
              == 0) continue;
          Instant at =
              PracticeScheduleService.occurrence(d, LocalTime.parse(row.localTime()), zone);
          if (at == null || at.isAfter(now) || !at.plusSeconds(1800).isAfter(now)) continue;
          if (inAppReminders) inAppReminder(row, at);
          if (row.emailReminderEnabled() && remindersEnabled && mail.available()) {
            var scheduledFor = LocalDateTime.ofInstant(at, ZoneOffset.UTC);
            persistence.queueReminder(scheduledFor, scheduledFor.plusMinutes(30), scheduledFor, row.id(), row.version(), scheduledFor);
          }
        }
      }
    }
  }

  private void inAppReminder(AccountMailRepository.Schedule schedule, Instant at) {
    persistence.queueNotification("reminder:" + schedule.id() + ":" + at, schedule.id(), schedule.version(), LocalDateTime.ofInstant(at, ZoneOffset.UTC));
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
    if (reminder) persistence.expireReminderClaims();


    persistence.expireDeliveries(table);
    var rows =
        persistence.dueDeliveries(table);
    for (var row : rows) {
      String claim = UUID.randomUUID().toString();
      long id = row.id();
      int claimed =
          persistence.claimDelivery(table, claim, id);
      if (claimed == 0) continue;
      try {
        AccountMailRepository.MailMessage message = tx.execute(s -> eligible(table, id, claim));
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
                  + cipher.decrypt(message.encryptedPayload());
          mail.send(
              message.email(),
              "Khôi phục mật khẩu",
              "Đặt lại mật khẩu bằng liên kết một lần:\n"
                  + url
                  + "\nNếu bạn không yêu cầu, hãy bỏ qua email này.");
        } else {
          String minutes = message.dailyGoalMinutes();
          mail.send(
              message.email(),
              "Đến giờ luyện tiếng Anh rồi!",
              "Bạn đã lên lịch luyện lúc "
                  + message.localTime()
                  + " ("
                  + message.timeZone()
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
        persistence.retryDelivery(table, id, claim);
      }
    }
    // A crash on the final attempt must not leave a PROCESSING row forever.
    persistence.failExhaustedClaims(table);
  }

  private AccountMailRepository.MailMessage eligible(String table, long id, String claim) {
    if (table.equals("account_mail_outbox"))
      return persistence.eligibleReset(id, claim)
          .orElse(null);
    return persistence.eligibleReminder(id, claim)
        .orElse(null);
  }

  private void finish(String table, long id, String claim, String status, String error) {
    persistence.finishDelivery(table, status, status, error, id, claim);
  }

  @Scheduled(fixedDelayString = "${app.account.cleanup-ms:300000}")
  public void cleanup() {
    persistence.clearExpiredPayloads();
    persistence.purgeTokens();
    persistence.purgeResetMail();
    persistence.purgeReminders();
    persistence.purgeRateBuckets();
  }
}
