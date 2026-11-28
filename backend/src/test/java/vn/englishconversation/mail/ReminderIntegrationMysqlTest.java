package vn.englishconversation.mail;


import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import vn.englishconversation.conversation.dto.*;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class ReminderIntegrationMysqlTest extends FullSystemMysqlFixture {

  @Test
  @EnabledIfEnvironmentVariable(named="G8_MAILPIT_TEST",matches="true")
  void reminderWorkersSendOnceThroughMailpit() throws Exception {
    var account = accounts.register("g8-reminder-" + java.util.UUID.randomUUID() + "@example.test",
        "Synthetic G8 reminder password");
    long owner = Long.parseLong(account.id());
    var now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1);
    jdbc.update("""
        INSERT INTO practice_schedules(user_id,local_time,time_zone,enabled,email_reminder_enabled,created_at,updated_at)
        VALUES (?,?,'UTC',TRUE,TRUE,UTC_TIMESTAMP(6)-INTERVAL 1 DAY,UTC_TIMESTAMP(6)-INTERVAL 1 DAY)
        """, owner,java.sql.Time.valueOf(now.toLocalTime()));
    jdbc.update("INSERT INTO practice_schedule_days SELECT id,? FROM practice_schedules WHERE user_id=?",
        now.getDayOfWeek().getValue(),owner);
    var a = CompletableFuture.runAsync(reminderWorker::tick);
    var b = CompletableFuture.runAsync(reminderWorker::tick);
    CompletableFuture.allOf(a,b).get(20, TimeUnit.SECONDS);
    assertEquals(1,jdbc.queryForObject("""
        SELECT COUNT(*) FROM practice_reminder_deliveries d JOIN practice_schedules s ON s.id=d.schedule_id
        WHERE s.user_id=? AND d.status='SENT' AND d.attempt_count=1
        """,Integer.class,owner));
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND kind='REMINDER'",Integer.class,owner));
    var mailbox = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
        java.net.URI.create("http://127.0.0.1:18025/api/v1/messages")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(200,mailbox.statusCode());
    assertEquals(1,new tools.jackson.databind.ObjectMapper().readTree(mailbox.body()).get("messages").size());
    assertTrue(mailbox.body().contains(account.email()));
  }

  @Test
  void reminderRejectsStaleSchedulesAndDoesNotRetryAmbiguousDelivery(
      @Autowired vn.englishconversation.common.repository.JdbcStore store,
      @Autowired vn.englishconversation.auth.security.TokenCipher cipher,
      @Autowired org.springframework.transaction.PlatformTransactionManager manager,
      @Autowired org.springframework.core.env.Environment environment) {
    var mail = org.mockito.Mockito.mock(vn.englishconversation.mail.provider.MailGateway.class);
    org.mockito.Mockito.when(mail.available()).thenReturn(true);
    var worker = new vn.englishconversation.mail.job.AccountMailWorker(new vn.englishconversation.mail.repository.AccountMailRepository(store,jdbc),mail,cipher,lifecycle,manager,new vn.englishconversation.config.features.FeatureSet(vn.englishconversation.config.features.FeatureProperties.Stage.FULL),true);
    for (String scenario : List.of("connect","timeout","stale","locked","deleting")) {
      var account = accounts.register(scenario + "-reminder-" + java.util.UUID.randomUUID() + "@example.test",
          "Synthetic notification password");
      long owner = Long.parseLong(account.id());
      if (scenario.equals("locked")) jdbc.update("UPDATE users SET status='LOCKED' WHERE id=?",owner);
      if (scenario.equals("deleting")) jdbc.update("UPDATE users SET deletion_requested_at=UTC_TIMESTAMP(6) WHERE id=?",owner);
      jdbc.update("""
          INSERT INTO practice_schedules(user_id,local_time,time_zone,enabled,email_reminder_enabled,version,created_at,updated_at)
          VALUES (?,'12:00:00','UTC',TRUE,TRUE,2,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
          """,owner);
      jdbc.update("""
          INSERT INTO practice_reminder_deliveries(schedule_id,schedule_version,scheduled_for,expires_at,next_attempt_at,created_at)
          SELECT id,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 30 MINUTE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
          FROM practice_schedules WHERE user_id=?
          """,scenario.equals("stale") ? 1 : 2,owner);
      if (scenario.equals("connect") || scenario.equals("timeout")) {
        var failure = scenario.equals("connect") ? new java.net.ConnectException() : new java.net.SocketTimeoutException();
        var messageFailure = new jakarta.mail.MessagingException("Fixture transport", failure);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException(
                "Fixture transport", null, java.util.Map.of("message", messageFailure)))
            .when(mail).send(org.mockito.ArgumentMatchers.eq(account.email()),org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
      }
      worker.tick();
      String expected = switch (scenario) { case "connect" -> "RETRY"; case "timeout" -> "UNKNOWN"; default -> "CANCELLED"; };
      assertEquals(expected,jdbc.queryForObject("""
          SELECT d.status FROM practice_reminder_deliveries d JOIN practice_schedules s ON s.id=d.schedule_id
          WHERE s.user_id=?
          """,String.class,owner));
      if (expected.equals("CANCELLED")) org.mockito.Mockito.verify(mail,org.mockito.Mockito.never())
          .send(org.mockito.ArgumentMatchers.eq(account.email()),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
      if (scenario.equals("connect") || scenario.equals("timeout")) {
        jdbc.update("""
            UPDATE practice_reminder_deliveries d JOIN practice_schedules s ON s.id=d.schedule_id
            SET d.next_attempt_at=UTC_TIMESTAMP(6) WHERE s.user_id=?
            """,owner);
        worker.tick();
        org.mockito.Mockito.verify(mail, org.mockito.Mockito.times(scenario.equals("connect") ? 2 : 1))
            .send(org.mockito.ArgumentMatchers.eq(account.email()),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
      }
      jdbc.update("DELETE FROM practice_schedules WHERE user_id=?",owner);
    }
  }
}
