package vn.englishconversation.pronunciation.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.pronunciation.dto.PronunciationData.*;
import vn.englishconversation.pronunciation.job.PronunciationWorker;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("g5")
@EnabledIfEnvironmentVariable(named = "G5_MYSQL_TEST", matches = "true")
class PronunciationMysqlTest {
  private static final String SEND = "pronunciation/drills/1/attempts";
  private static final String HISTORY = "pronunciation/attempts";
  private final ObjectMapper json = new ObjectMapper();
  @Autowired JdbcTemplate db;
  @Autowired PronunciationWorker worker;
  @Autowired PronunciationService service;
  @Autowired TransactionTemplate transactions;
  @MockitoBean PronunciationProvider provider;
  @Value("${local.server.port}") int port;

  @DynamicPropertySource
  static void isolated(DynamicPropertyRegistry registry) throws IOException {
    var directory = Files.createTempDirectory(Path.of("target"), "pronunciation-test-").toAbsolutePath();
    registry.add("spring.datasource.url", () -> testUrl("G5_TEST_DB_URL"));
    registry.add("spring.datasource.username", () -> System.getenv("SPRING_DATASOURCE_USERNAME"));
    registry.add("spring.datasource.password", () -> System.getenv("SPRING_DATASOURCE_PASSWORD"));
    registry.add("app.pronunciation.audio-directory", directory::toString);
    registry.add("app.pronunciation.fingerprint-key", () -> "fixture-only-secret-not-for-runtime");
    registry.add("app.pronunciation.poll-ms", () -> 300);
    registry.add("app.pronunciation.cleanup-ms", () -> 300);
    registry.add("app.pronunciation.daily-request-limit", () -> 6);
    registry.add("app.pronunciation.provider-daily-limit", () -> 40);
    registry.add("app.mail.enabled", () -> false);
    registry.add("app.admin-bootstrap.enabled", () -> false);
    registry.add("server.servlet.session.cookie.secure", () -> false);
  }

  @Test
  void upgradesV12AndPreservesListeningAndLessonData() {
    String url = testUrl("G5_UPGRADE_DB_URL");
    var baseline = Flyway.configure().dataSource(url, System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD")).target("12").load();
    assertEquals(12, baseline.migrate().migrationsExecuted);
    var upgrade = Flyway.configure().dataSource(url, System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD")).load();
    assertEquals(21, upgrade.migrate().migrationsExecuted);
    assertEquals("33", upgrade.info().current().getVersion().getVersion());
    assertEquals(0, upgrade.migrate().migrationsExecuted);
    var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(url, System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
    var upgraded = new JdbcTemplate(source);
    assertEquals(8, upgraded.queryForObject("SELECT COUNT(*) FROM listening_exercises", Integer.class));
    assertEquals(8, upgraded.queryForObject("SELECT COUNT(*) FROM lessons", Integer.class));
    assertEquals(9, upgraded.queryForObject("SELECT COUNT(*) FROM pronunciation_drills", Integer.class));
  }

  @Test
  void validatesAudioCsrfOwnershipIdempotencyAndHistorySnapshot() throws Exception {
    when(provider.available()).thenReturn(true);
    var score = new BigDecimal("88.00");
    when(provider.assess(any(), anyString(), eq("en-US"))).thenReturn(new Assessment("Hello.", score,
        score, score, score, List.of(new Word("hello", score, null, 0L, 450L,
            List.of(new Phoneme("ɛ", score, 50L, 100L))))));
    var owner = new Browser("owner");
    var other = new Browser("other");
    assertEquals(401, new Browser().get(HISTORY).statusCode());
    assertEquals(9, owner.json(owner.get("pronunciation/drills"), 200).path("drills").size());
    assertEquals(403, owner.sendWave(key(), wave(1, 10), "audio/wav", false).join().statusCode());
    assertEquals(400, owner.sendWave(key(), wave(1, 0), "audio/wav", true).join().statusCode());
    assertEquals(400, owner.sendWave(key(), wave(1, 2), "audio/webm", true).join().statusCode());
    var stereo = wave(1, 10);
    stereo[22] = 2;
    assertEquals(400, owner.sendWave(key(), stereo, "audio/wav", true).join().statusCode());
    assertEquals(413, owner.sendWave(key(), wave(61, 10), "audio/wav", true).join().statusCode());
    assertEquals(422, owner.sendWave(key(), wave(31, 10), "audio/wav", true).join().statusCode());

    String key = key();
    var first = owner.sendWave(key, wave(1, 10), "audio/wav", true);
    var duplicate = owner.sendWave(key, wave(1, 10), "audio/wav", true);
    long id = owner.json(first.join(), 202).path("id").asLong();
    assertEquals(id, owner.json(duplicate.join(), 202).path("id").asLong());
    assertEquals(409, owner.sendWave(key, wave(1, 11), "audio/wav", true).join().statusCode());
    assertEquals(404, other.get(HISTORY + "/" + id).statusCode());

    var reading = owner.awaitStatus(id, "SUCCEEDED");
    assertEquals(88, reading.path("overall").asInt());
    assertEquals("ɛ", reading.path("words").path(0).path("phonemes").path(0).path("expectedIpa").asString());
    assertEquals("Hello.", reading.path("recognizedText").asString());
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM ai_attempts a JOIN pronunciation_attempts p ON p.ai_request_id=a.ai_request_id WHERE p.id=?", Integer.class, id));
    awaitAudioRemoved(id);
    try {
      db.update("UPDATE pronunciation_drills SET reference_text='Good morning.' WHERE id=1");
      assertEquals("Hello.", owner.json(owner.get(HISTORY + "/" + id), 200).path("referenceText").asString());
    } finally {
      db.update("UPDATE pronunciation_drills SET reference_text='Hello.' WHERE id=1");
    }
    assertEquals(204, owner.mutate("DELETE", "learning/data", null).statusCode());
    assertEquals(0, owner.json(owner.get(HISTORY), 200).path("attempts").size());
    assertEquals(200, owner.get("auth/me").statusCode());
  }

  @Test
  void distinguishesNoSpeechRetryAndUnknownWithoutInventingScores() throws Exception {
    when(provider.available()).thenReturn(true);
    when(provider.assess(any(), anyString(), anyString()))
        .thenThrow(new PronunciationProvider.Failure("AZURE_QUOTA", true, false))
        .thenReturn(null)
        .thenThrow(new PronunciationProvider.Failure("AZURE_OUTCOME_UNKNOWN", false, true));
    var owner = new Browser("retry");
    var id = owner.submit();
    for (int index = 0; index < 50; index++) {
      if (db.update("""
          UPDATE ai_requests r JOIN pronunciation_attempts p ON p.ai_request_id=r.id
          SET retry_not_before=UTC_TIMESTAMP(6) WHERE p.id=? AND r.retryable=TRUE
          """, id) > 0) break;
      Thread.sleep(100);
    }
    var noSpeech = owner.awaitStatus(id, "INSUFFICIENT_EVIDENCE");
    assertTrue(noSpeech.path("overall").isNull());
    assertTrue(noSpeech.path("words").isEmpty());
    assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM ai_attempts a JOIN pronunciation_attempts p ON p.ai_request_id=a.ai_request_id WHERE p.id=?", Integer.class, id));
    awaitAudioRemoved(id);
    var timedOut = owner.submit();
    assertTrue(owner.awaitStatus(timedOut, "UNKNOWN").path("accuracy").isNull());
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM ai_attempts a JOIN pronunciation_attempts p ON p.ai_request_id=a.ai_request_id WHERE p.id=?", Integer.class, timedOut));
    awaitAudioRemoved(timedOut);
  }

  @Test
  void cleanupRetriesAndLateProviderCannotRestoreDeletedResult() throws Exception {
    when(provider.available()).thenReturn(true);
    var location = new CompletableFuture<Path>();
    when(provider.assess(any(), anyString(), anyString())).thenAnswer(invocation -> {
      Path path = invocation.getArgument(0);
      Files.delete(path);
      Files.createDirectory(path);
      Files.writeString(path.resolve("fixture"), "block delete");
      location.complete(path);
      return null;
    });
    var owner = new Browser("cleanup");
    long id = owner.submit();
    var blocked = location.get(5, TimeUnit.SECONDS);
    owner.awaitStatus(id, "INSUFFICIENT_EVIDENCE");
    assertTrue(Files.exists(blocked.resolve("fixture")));
    assertNotNull(db.queryForObject("SELECT audio_storage_key FROM pronunciation_attempts WHERE id=?", String.class, id));
    Files.delete(blocked.resolve("fixture"));
    worker.cleanup();
    awaitAudioRemoved(id);
    assertFalse(Files.exists(blocked));

    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var returned = new CountDownLatch(1);
    doAnswer(invocation -> {
      entered.countDown();
      try {
        assertTrue(release.await(10, TimeUnit.SECONDS));
        return null;
      } finally { returned.countDown(); }
    }).when(provider).assess(any(), anyString(), anyString());
    var lateId = owner.submit();
    try {
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertEquals(204, owner.mutate("DELETE", "learning/data", null).statusCode());
    } finally { release.countDown(); }
    assertTrue(returned.await(5, TimeUnit.SECONDS));
    assertEquals(404, owner.get(HISTORY + "/" + lateId).statusCode());
    assertEquals(0, owner.json(owner.get(HISTORY), 200).path("attempts").size());
  }

  @Test
  void expiresUnclaimedAudioAndKeepsQuotaAcrossLearningDataDeletion() throws Exception {
    when(provider.available()).thenReturn(true);
    var owner = new Browser("quota");
    doAnswer(invocation -> null).when(provider).assess(any(), anyString(), anyString());
    for (int count = 0; count < 5; count++)
      owner.awaitStatus(owner.submit(), "INSUFFICIENT_EVIDENCE");

    long user = owner.json(owner.get("auth/me"), 200).path("id").asLong();
    long expiring = transactions.execute(transaction -> {
      var file = new MockMultipartFile("audio", "sample.wav", "audio/wav", wave(1, 20));
      long id = service.submit(user, 1, key(), file).id();
      db.update("UPDATE pronunciation_attempts SET audio_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?", id);
      return id;
    });
    worker.cleanup();
    assertEquals("PROCESSING_EXPIRED", owner.awaitStatus(expiring, "FAILED").path("errorCode").asString());
    awaitAudioRemoved(expiring);
    assertEquals(204, owner.mutate("DELETE", "learning/data", null).statusCode());
    assertEquals(429, owner.sendWave(key(), wave(1, 20), "audio/wav", true).join().statusCode());
  }

  private void awaitAudioRemoved(long id) throws InterruptedException {
    for (int tries = 0; tries < 60; tries++) {
      if (db.queryForObject("SELECT audio_deleted_at IS NOT NULL FROM pronunciation_attempts WHERE id=?",
          Boolean.class, id)) return;
      Thread.sleep(100);
    }
    fail("Expected stored audio to be removed");
  }

  private static String key() { return UUID.randomUUID().toString(); }

  private static String testUrl(String name) {
    String value = System.getenv(name);
    if (value == null || !value.matches("jdbc:mysql://127\\.0\\.0\\.1:(3306|3307|13306)/g5_test_[0-9a-f]{12}\\?connectionTimeZone=UTC"))
      throw new IllegalStateException("Use dedicated G5 schemas on MySQL test port");
    return value;
  }

  private static byte[] wave(int seconds, int amplitude) {
    int bytes = seconds * 32000;
    var wav = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN);
    wav.putInt(0x46464952).putInt(bytes + 36).putInt(0x45564157).putInt(0x20746d66).putInt(16)
        .putShort((short) 1).putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2)
        .putShort((short) 16).putInt(0x61746164).putInt(bytes);
    while (wav.hasRemaining()) wav.putShort((short) (wav.position() % 4 == 0 ? amplitude : -amplitude));
    return wav.array();
  }

  private class Browser {
    final HttpClient http = HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

    Browser() {}
    Browser(String name) throws Exception {
      var credentials = Map.of("email", name + "-" + key() + "@example.test", "password", "Synthetic G5 test passphrase");
      assertEquals(201, mutate("POST", "auth/register", credentials).statusCode());
      assertEquals(200, mutate("POST", "auth/login", credentials).statusCode());
    }

    HttpResponse<String> get(String path) throws Exception {
      return http.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> mutate(String method, String path, Object body) throws Exception {
      var request = csrf(request(path)).header("Content-Type", "application/json");
      return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
          : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    CompletableFuture<HttpResponse<String>> sendWave(String key, byte[] wav, String mime, boolean csrf)
        throws Exception {
      var boundary = "G5-" + key();
      var body = new ByteArrayOutputStream();
      body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n" + key
          + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"sample.wav\"\r\n"
          + "Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
      body.writeBytes(wav);
      body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
      var request = request(SEND).header("Content-Type", "multipart/form-data; boundary=" + boundary);
      if (csrf) csrf(request);
      return http.sendAsync(request.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
          HttpResponse.BodyHandlers.ofString());
    }

    long submit() throws Exception { return json(sendWave(key(), wave(1, 20), "audio/wav", true).join(), 202).path("id").asLong(); }

    JsonNode awaitStatus(long id, String status) throws Exception {
      JsonNode last = null;
      for (int tries = 0; tries < 70; tries++) {
        last = json(get(HISTORY + "/" + id), 200);
        if (status.equals(last.path("status").asString())) return last;
        Thread.sleep(100);
      }
      return fail("Expected " + status + ", got " + last);
    }

    JsonNode json(HttpResponse<String> response, int code) {
      assertEquals(code, response.statusCode(), response.body());
      return json.readTree(response.body());
    }

    private HttpRequest.Builder csrf(HttpRequest.Builder request) throws Exception {
      var token = json(get("auth/csrf"), 200);
      return request.header(token.path("headerName").asString(), token.path("token").asString());
    }

    private HttpRequest.Builder request(String path) {
      return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/" + path));
    }
  }
}
