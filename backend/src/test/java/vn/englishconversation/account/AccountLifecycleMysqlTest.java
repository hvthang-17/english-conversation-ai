package vn.englishconversation.account;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.auth.job.AccountDeletionWorker;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.security.TokenCipher;
import vn.englishconversation.auth.service.AccountLifecycleService;
import vn.englishconversation.auth.service.AccountRateLimiter;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.mail.job.AccountMailWorker;
import vn.englishconversation.mail.provider.MailGateway;
import vn.englishconversation.profile.service.AvatarService;
import vn.englishconversation.profile.service.PracticeScheduleService;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.mail.reminders-enabled=true",
      "app.mail.enabled=true",
      "app.mail.host=127.0.0.1",
      "app.mail.from=fixture@example.test",
      "app.mail.outbox-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "app.mail.poll-ms=3600000",
      "app.account.cleanup-ms=3600000"
    })
@org.springframework.test.context.ActiveProfiles("g1")
@EnabledIfEnvironmentVariable(named = "G1_MYSQL_TEST", matches = "true")
class AccountLifecycleMysqlTest {
  @Autowired AccountLifecycleService lifecycle;
  @Autowired AccountRateLimiter rateLimiter;
  @Autowired AccountService accounts;
  @Autowired JdbcTemplate jdbc;
  @Autowired AccountMailWorker worker;
  @Autowired TokenCipher cipher;
  @Autowired PracticeScheduleService schedules;
  @Autowired AvatarService avatars;
  @Autowired AccountDeletionWorker deletion;
  @Autowired FakeMail mail;

  @Value("${local.server.port}")
  int port;

  static final String PASSWORD = "Synthetic lifecycle passphrase";
  final ObjectMapper json = new ObjectMapper();

  @DynamicPropertySource
  static void isolated(DynamicPropertyRegistry props) {
    String url = System.getenv("G1_TEST_DB_URL");
    if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:(?:3306|3307|13306)/g1_test_[0-9a-f]{12}\\?connectionTimeZone=UTC"))
      throw new IllegalStateException("Use the isolated G1 MySQL test database");
    props.add("spring.datasource.url", () -> url);
  }

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    FakeMail fakeMail() {
      return new FakeMail();
    }

  }

  @Test
  void concurrentRateBucketDoesNotUpgradeSharedLocks() throws Exception {
    String bucket = "rate-race:" + UUID.randomUUID();
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> call =
          () -> {
            rateLimiter.check(bucket, 10, 0);
            return true;
          };
      for (var result : pool.invokeAll(List.of(call, call))) assertTrue(result.get());
    }
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT request_count FROM account_rate_limits WHERE bucket_key=?",
            Integer.class,
            TokenCipher.hash(bucket)));
  }

  @Test
  void expiredAndCancelledReminderNeverSendsAndAbandonedClaimStaysUnknown() {
    var p = person();
    schedules.save(
        p.principal(), new PracticeScheduleService.Input(List.of(1), "20:00", "UTC", true, true));
    long schedule =
        jdbc.queryForObject("SELECT id FROM practice_schedules WHERE user_id=?", Long.class, p.id);
    jdbc.update(
        "INSERT INTO"
            + " practice_reminder_deliveries(schedule_id,schedule_version,scheduled_for,expires_at,status,attempt_count,next_attempt_at,lease_until,claim_token,created_at)"
            + " VALUES (?,1,UTC_TIMESTAMP(6)-INTERVAL 2 MINUTE,UTC_TIMESTAMP(6)+INTERVAL 28"
            + " MINUTE,'PROCESSING',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)-INTERVAL 1"
            + " MINUTE,'stale-claim',UTC_TIMESTAMP(6))",
        schedule);
    worker.tick();
    assertEquals(
        "UNKNOWN",
        jdbc.queryForObject(
            "SELECT status FROM practice_reminder_deliveries WHERE schedule_id=?",
            String.class,
            schedule));
    assertEquals(0, mail.messages.stream().filter(m -> m.to.equals(p.email)).count());
    assertEquals(1, jdbc.queryForObject(
        "SELECT attempt_count FROM practice_reminder_deliveries WHERE schedule_id=?", Integer.class, schedule));
    assertEquals("DELIVERY_UNKNOWN", jdbc.queryForObject(
        "SELECT error_code FROM practice_reminder_deliveries WHERE schedule_id=?", String.class, schedule));
    long count = mail.messages.stream().filter(m -> m.to.equals(p.email)).count();
    jdbc.update(
        "INSERT INTO"
            + " practice_reminder_deliveries(schedule_id,schedule_version,scheduled_for,expires_at,next_attempt_at,created_at)"
            + " VALUES (?,1,UTC_TIMESTAMP(6)-INTERVAL 1 HOUR,UTC_TIMESTAMP(6)-INTERVAL 30"
            + " MINUTE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        schedule);
    worker.tick();
    assertEquals(count, mail.messages.stream().filter(m -> m.to.equals(p.email)).count());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM practice_reminder_deliveries WHERE schedule_id=? AND"
                + " status='EXPIRED'",
            Integer.class,
            schedule));
    jdbc.update(
        "INSERT INTO"
            + " practice_reminder_deliveries(schedule_id,schedule_version,scheduled_for,expires_at,next_attempt_at,created_at)"
            + " VALUES (?,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 30"
            + " MINUTE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        schedule);
    schedules.save(
        p.principal(), new PracticeScheduleService.Input(List.of(1), "20:00", "UTC", false, false));
    worker.tick();
    assertEquals(count, mail.messages.stream().filter(m -> m.to.equals(p.email)).count());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM practice_reminder_deliveries WHERE schedule_id=? AND"
                + " status='CANCELLED'",
            Integer.class,
            schedule));
  }

  @Test
  void fileCleanupFailurePreservesReferencesAndRetriesDeletion() throws Exception {
    var p = person();
    String key = UUID.randomUUID().toString();
    var directory =
        java.nio.file.Path.of(".runtime/avatars", key + ".png").toAbsolutePath().normalize();
    assertTrue(
        directory.startsWith(
            java.nio.file.Path.of(".runtime/avatars").toAbsolutePath().normalize()));
    java.nio.file.Files.createDirectories(directory);
    var marker = directory.resolve("synthetic-file");
    java.nio.file.Files.writeString(marker, "fixture");
    jdbc.update("UPDATE learner_profiles SET avatar_storage_key=? WHERE user_id=?", key, p.id);
    try {
      lifecycle.delete(p.principal(), PASSWORD, "DELETE");
      deletion.sweep();
      assertEquals(
          "RETRY",
          jdbc.queryForObject(
              "SELECT status FROM account_deletion_jobs WHERE subject_user_id=?",
              String.class,
              p.id));
      assertEquals(
          key,
          jdbc.queryForObject(
              "SELECT avatar_storage_key FROM learner_profiles WHERE user_id=?",
              String.class,
              p.id));
    } finally {
      java.nio.file.Files.deleteIfExists(marker);
      java.nio.file.Files.deleteIfExists(directory);
    }
    jdbc.update(
        "UPDATE account_deletion_jobs SET next_attempt_at=UTC_TIMESTAMP(6) WHERE subject_user_id=?",
        p.id);
    deletion.sweep();
    assertEquals(
        "COMPLETED",
        jdbc.queryForObject(
            "SELECT status FROM account_deletion_jobs WHERE subject_user_id=?",
            String.class,
            p.id));
    assertEquals(
        0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=?", Integer.class, p.id));
  }

  static class FakeMail implements MailGateway {
    record Message(String to, String subject, String body) {}

    final List<Message> messages = new CopyOnWriteArrayList<>();
    volatile boolean fail;

    public boolean available() {
      return true;
    }

    public void send(String to, String subject, String text) {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      if (fail) throw new org.springframework.mail.MailSendException(
          "Synthetic SMTP connection failure", new java.net.ConnectException("Fixture"));
      messages.add(new Message(to, subject, text));
    }
  }

  class Browser {
    final HttpClient client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();

    HttpResponse<String> call(String method, String path, Object body, boolean csrf)
        throws Exception {
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/" + path))
              .header("Content-Type", "application/json");
      if (csrf) {
        var t = json.readTree(call("GET", "auth/csrf", null, false).body());
        request.header(t.get("headerName").asString(), t.get("token").asString());
      }
      return client.send(
          request
              .method(
                  method,
                  body == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }

    void login(String email) throws Exception {
      assertEquals(
          200,
          call("POST", "auth/login", Map.of("email", email, "password", PASSWORD), true)
              .statusCode());
    }
  }

  record Person(long id, String email) {
    AccountPrincipal principal() {
      return new AccountPrincipal(id, 0);
    }
  }

  Person person() {
    String email = "lifecycle-" + UUID.randomUUID() + "@example.test";
    return new Person(Long.parseLong(accounts.register(email, PASSWORD).id()), email);
  }

  String token(Person p) {
    return cipher.decrypt(
        jdbc.queryForObject(
            "SELECT o.encrypted_payload FROM account_mail_outbox o JOIN account_action_tokens t ON"
                + " t.id=o.action_token_id WHERE o.user_id=? ORDER BY o.id DESC"
                + " LIMIT 1",
            String.class,
            p.id));
  }

  @AfterEach
  void reset() {
    mail.fail = false;
  }

  @Test
  void signupQueuesNoMailAndCanUseProfileWhileConversationIsDisabled() throws Exception {
    var p = person();
    var browser = new Browser();
    browser.login(p.email);
    assertEquals(200, browser.call("GET", "profile/me", null, false).statusCode());
    assertEquals(0, jdbc.queryForObject(
        "SELECT COUNT(*) FROM account_mail_outbox WHERE user_id=?", Integer.class, p.id));
    assertEquals(403, browser.call("POST", "auth/email-verification/request", Map.of(), true).statusCode());
    assertEquals(403, browser.call("POST", "practice-sessions", Map.of(), true).statusCode());
  }

  @Test
  void profileAndLearningDataActionsStayScopedToTheSessionOwner() throws Exception {
    var owner = person();
    var other = person();
    var browser = new Browser();
    browser.login(owner.email);
    String topic = json.readTree(browser.call("GET", "topics", null, false).body()).get(0).get("id").asString();
    var profile = new HashMap<String, Object>(Map.of(
        "displayName", "Hồ sơ thử nghiệm",
        "selectedLevel", "B1",
        "learningGoal", "Practice travel English",
        "dailyGoalMinutes", 20,
        "topicIds", List.of(topic),
        "userId", Long.toString(other.id)));
    assertEquals(200, browser.call("PUT", "profile/me", profile, true).statusCode());
    profile.put("selectedLevel", "C9");
    assertEquals(400, browser.call("PUT", "profile/me", profile, true).statusCode());
    schedules.save(owner.principal(), new PracticeScheduleService.Input(List.of(1), "20:00", "UTC", true, true));
    assertEquals(403, browser.call("DELETE", "learning/data", null, false).statusCode());
    assertEquals(204, browser.call("DELETE", "learning/data", null, true).statusCode());
    assertEquals(404, browser.call("DELETE", "learning/history/1", null, true).statusCode());
    var saved = json.readTree(browser.call("GET", "profile/me", null, false).body());
    assertEquals("B1", saved.get("selectedLevel").asString());
    assertEquals(topic, saved.get("topicIds").get(0).asString());
    assertTrue(schedules.get(owner.id).emailReminderEnabled());
    assertEquals("Người học", jdbc.queryForObject(
        "SELECT display_name FROM learner_profiles WHERE user_id=?", String.class, other.id));
  }

  @Test
  void expiryAndResendInvalidateOldToken() {
    var p = person();
    lifecycle.requestReset(p.email);
    String prior = token(p);
    jdbc.update("DELETE FROM account_rate_limits WHERE bucket_key=?", TokenCipher.hash("reset:" + p.email));
    lifecycle.requestReset(p.email);
    String next = token(p);
    assertNotEquals(prior, next);
    assertThrows(ApiException.class, () -> lifecycle.confirmReset(prior, PASSWORD));
    jdbc.update(
        "UPDATE account_action_tokens SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE"
            + " user_id=?",
        p.id);
    assertThrows(ApiException.class, () -> lifecycle.confirmReset(next, PASSWORD));
    assertEquals(
        429,
        assertThrows(ApiException.class, () -> lifecycle.requestReset(p.email))
            .status);
  }

  @Test
  void resetRevokesAllSessionsInBothBrowsers() throws Exception {
    var p = person();
    var a = new Browser();
    var b = new Browser();
    a.login(p.email);
    b.login(p.email);
    lifecycle.requestReset(p.email);
    String reset = token(p);
    assertEquals(
        204,
        a.call(
                "POST",
                "auth/password-reset/confirm",
                Map.of("token", reset, "newPassword", "Replacement long passphrase"),
                true)
            .statusCode());
    assertEquals(401, b.call("GET", "auth/me", null, false).statusCode());
    assertEquals(401, a.call("GET", "auth/me", null, false).statusCode());
    assertThrows(ApiException.class, () -> lifecycle.confirmReset(reset, PASSWORD));
    assertThrows(ApiException.class, () -> accounts.login(p.email, PASSWORD));
    assertNotNull(accounts.login(p.email, "Replacement long passphrase"));
  }

  @Test
  void resetRequestHasSameStatusForUnknownAndLockedAndRejectsMissingCsrf() throws Exception {
    var browser = new Browser();
    var p = person();
    jdbc.update("UPDATE users SET status='LOCKED' WHERE id=?", p.id);
    assertEquals(
        202,
        browser
            .call("POST", "auth/password-reset/request", Map.of("email", p.email), true)
            .statusCode());
    assertEquals(
        202,
        browser
            .call(
                "POST",
                "auth/password-reset/request",
                Map.of("email", "absent-" + UUID.randomUUID() + "@example.test"),
                true)
            .statusCode());
    assertEquals(
        403,
        browser
            .call("POST", "auth/password-reset/request", Map.of("email", p.email), false)
            .statusCode());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM account_action_tokens WHERE user_id=?",
            Integer.class,
            p.id));
  }

  @Test
  void passwordResetMailRetriesAndErasesCiphertextAfterDelivery() {
    var p = person();
    lifecycle.requestReset(p.email);
    mail.fail = true;
    worker.tick();
    assertEquals("RETRY", jdbc.queryForObject(
        "SELECT status FROM account_mail_outbox WHERE user_id=?", String.class, p.id));
    assertNotNull(jdbc.queryForObject(
        "SELECT encrypted_payload FROM account_mail_outbox WHERE user_id=?", String.class, p.id));
    mail.fail = false;
    jdbc.update("UPDATE account_mail_outbox SET next_attempt_at=UTC_TIMESTAMP(6) WHERE user_id=?", p.id);
    worker.tick();
    assertEquals("SENT", jdbc.queryForObject(
        "SELECT status FROM account_mail_outbox WHERE user_id=?", String.class, p.id));
    assertNull(jdbc.queryForObject(
        "SELECT encrypted_payload FROM account_mail_outbox WHERE user_id=?", String.class, p.id));
    assertEquals(1, mail.messages.stream()
        .filter(message -> message.to().equals(p.email) && message.body().contains("/#/reset-password?token="))
        .count());
  }

  @Test
  void scheduleIsOwnerOnlyValidatesAndAvatarNeverUsesClientPath() throws Exception {
    var p = person();
    var other = person();
    var browser = new Browser();
    browser.login(p.email);
    assertEquals("null", browser.call("GET", "profile/me/practice-schedule", null, false).body());
    var body =
        Map.of(
            "days",
            List.of(1, 3, 5),
            "localTime",
            "20:00",
            "timeZone",
            "Asia/Ho_Chi_Minh",
            "enabled",
            true,
            "emailReminderEnabled",
            true,
            "userId",
            Long.toString(other.id));
    assertEquals(200, browser.call("PUT", "profile/me/practice-schedule", body, true).statusCode());
    assertNull(schedules.get(other.id));
    assertEquals(
        403, browser.call("PUT", "profile/me/practice-schedule", body, false).statusCode());
    assertEquals(
        400,
        browser
            .call(
                "PUT",
                "profile/me/practice-schedule",
                Map.of(
                    "days",
                    List.of(1, 1),
                    "localTime",
                    "20:00",
                    "timeZone",
                    "invalid",
                    "enabled",
                    true),
                true)
            .statusCode());
    assertEquals(
        401, new Browser().call("GET", "profile/me/practice-schedule", null, false).statusCode());
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(10, 10, 2), "png", bytes);
    avatars.save(
        p.principal(),
        new MockMultipartFile("file", "../../private.png", "image/png", bytes.toByteArray()));
    assertTrue(avatars.read(p.id).length > 0);
    assertThrows(ApiException.class, () -> avatars.read(other.id));
    avatars.delete(p.principal());
    assertThrows(ApiException.class, () -> avatars.read(p.id));
  }

  @Test
  void remindersAreUniqueAndCancelOnScheduleChangeAndRetryMailOutsideTransaction() {
    var p = person();
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
    var local = now.atZone(ZoneOffset.UTC);
    var input = new PracticeScheduleService.Input(
        List.of(local.getDayOfWeek().getValue()),
        local.toLocalTime().toString().substring(0, 5),
        "UTC", true, true);
    schedules.save(p.principal(), input);
    jdbc.update(
        "UPDATE practice_schedules SET updated_at=? WHERE user_id=?",
        Timestamp.from(now.minusSeconds(60)),
        p.id);
    worker.generate(now);
    worker.generate(now);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM practice_reminder_deliveries d JOIN practice_schedules s ON"
                + " s.id=d.schedule_id WHERE s.user_id=?",
            Integer.class,
            p.id));
    schedules.save(p.principal(), input);
    assertEquals(
        "PENDING",
        jdbc.queryForObject(
            "SELECT d.status FROM practice_reminder_deliveries d JOIN practice_schedules s ON"
                + " s.id=d.schedule_id WHERE s.user_id=?",
            String.class,
            p.id));
    mail.fail = true;
    worker.tick();
    assertEquals(
        "RETRY",
        jdbc.queryForObject(
            "SELECT d.status FROM practice_reminder_deliveries d JOIN practice_schedules s ON"
                + " s.id=d.schedule_id WHERE s.user_id=?",
            String.class,
            p.id));
    mail.fail = false;
    jdbc.update(
        "UPDATE practice_reminder_deliveries d JOIN practice_schedules s ON s.id=d.schedule_id SET"
            + " d.next_attempt_at=UTC_TIMESTAMP(6) WHERE s.user_id=?",
        p.id);
    worker.tick();
    assertEquals(
        "SENT",
        jdbc.queryForObject(
            "SELECT d.status FROM practice_reminder_deliveries d JOIN practice_schedules s ON"
                + " s.id=d.schedule_id WHERE s.user_id=?",
            String.class,
            p.id));
    long sent = mail.messages.stream().filter(m -> m.to.equals(p.email)).count();
    worker.tick();
    assertEquals(sent, mail.messages.stream().filter(m -> m.to.equals(p.email)).count());
    schedules.save(
        p.principal(), new PracticeScheduleService.Input(List.of(1), "20:00", "UTC", false, false));
    assertNull(schedules.get(p.id).nextOccurrenceAt());
  }

  @Test
  void deleteRevokesSessionsAndRemovesOwnerDataWhileKeepingCatalog() throws Exception {
    var p = person();
    var browser = new Browser();
    browser.login(p.email);
    schedules.save(
        p.principal(), new PracticeScheduleService.Input(List.of(1), "20:00", "UTC", true, true));
    assertEquals(
        400,
        browser
            .call(
                "DELETE",
                "auth/account",
                Map.of("currentPassword", "wrong", "confirmation", "DELETE"),
                true)
            .statusCode());
    assertEquals(
        202,
        browser
            .call(
                "DELETE",
                "auth/account",
                Map.of("currentPassword", PASSWORD, "confirmation", "DELETE"),
                true)
            .statusCode());
    assertEquals(401, browser.call("GET", "auth/me", null, false).statusCode());
    assertThrows(ApiException.class, () -> accounts.login(p.email, PASSWORD));
    deletion.sweep();
    assertEquals(
        0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=?", Integer.class, p.id));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM account_action_tokens WHERE user_id=?", Integer.class, p.id));
    assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM topics", Integer.class));
    assertEquals(
        "COMPLETED",
        jdbc.queryForObject(
            "SELECT status FROM account_deletion_jobs WHERE subject_user_id=?",
            String.class,
            p.id));
  }

  @Test
  void tokenRaceHasExactlyOneSuccessfulConsumer() throws Exception {
    var p = person();
    lifecycle.requestReset(p.email);
    String token = token(p);
    var executor = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> call =
          () -> {
            try {
              lifecycle.confirmReset(token, PASSWORD);
              return true;
            } catch (ApiException e) {
              return false;
            }
          };
      var futures = executor.invokeAll(List.of(call, call));
      int success = 0;
      for (var f : futures) if (f.get()) success++;
      assertEquals(1, success);
    } finally {
      executor.shutdownNow();
    }
  }
}
