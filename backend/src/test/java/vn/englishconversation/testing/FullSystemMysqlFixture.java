package vn.englishconversation.testing;

import vn.englishconversation.testing.FixtureProvider;

import vn.englishconversation.auth.service.AccountLifecycleService;
import vn.englishconversation.auth.job.AccountDeletionWorker;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.conversation.dto.*;
import vn.englishconversation.conversation.service.PracticeService;
import vn.englishconversation.voice.service.VoiceStorage;

@SpringBootTest(
    classes = vn.englishconversation.EnglishConversationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "CHAT_GLOBAL_RPM=60",
      "CHAT_USER_RPM=60",
      "CHAT_GLOBAL_CONCURRENT=1",
      "CHAT_FINGERPRINT_SECRET=synthetic-test-only-secret-1234567890",
      "CHAT_TOTAL_ATTEMPT_LIMIT=0",
      "GEMINI_CHAT_ENABLED=false",
      "app.pronunciation.fingerprint-key=synthetic-g8-fingerprint-test-only-value"
    })
@EnabledIfEnvironmentVariable(named = "PHASE1_MYSQL_TEST", matches = "true")
@ActiveProfiles("g6")
@Import(FullSystemMysqlFixture.FixtureConfig.class)
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.Random.class)
public abstract class FullSystemMysqlFixture {
  protected static final java.nio.file.Path AVATAR_TEST_DIRECTORY =
      java.nio.file.Path.of(".runtime", "g6-avatars", java.util.UUID.randomUUID().toString()).toAbsolutePath();
  @org.springframework.test.context.bean.override.mockito.MockitoBean
  protected vn.englishconversation.pronunciation.service.PronunciationProvider pronunciationProvider;
  @Autowired protected PracticeService service;
  @Autowired protected AccountService accounts;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected FixtureProvider provider;
  @Autowired protected AccountLifecycleService lifecycle;
  @Autowired protected AccountDeletionWorker deletion;
  @Autowired protected VoiceStorage voiceStorage;
  @Autowired protected vn.englishconversation.pronunciation.service.ConversationPronunciation conversationPronunciation;

  @org.springframework.beans.factory.annotation.Value("${local.server.port}")
  protected int port;

  @Autowired protected vn.englishconversation.learning.service.LearningService learning;
  @Autowired protected vn.englishconversation.admin.repository.AdminMetricsStore adminMetrics;
  @Autowired protected vn.englishconversation.conversation.service.ConversationHistoryService historyService;

  @BeforeEach
  protected void ageAttemptsFromPreviousTestsOutOfTheQuotaWindow() {
    provider.reset();
    jdbc.update("""
        UPDATE ai_attempts
        SET started_at=started_at-INTERVAL 61 SECOND,finished_at=finished_at-INTERVAL 61 SECOND
        WHERE status<>'RUNNING' AND started_at>=UTC_TIMESTAMP(6)-INTERVAL 60 SECOND
        """);
  }

  @Autowired protected vn.englishconversation.mail.job.AccountMailWorker reminderWorker;
  @Autowired protected vn.englishconversation.pronunciation.service.PronunciationService pronunciation;

  protected CreatePracticeRequest voicePractice() {
    var request = draft();
    return new CreatePracticeRequest(request.scenarioId(), request.level(), request.clientRequestId(), "VOICE");
  }

  protected MockMultipartFile wave(int frames, int sample, int format) {
    ByteBuffer bytes = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(0x46464952).putInt(bytes.capacity() - 8).putInt(0x45564157)
        .putInt(0x20746d66).putInt(16).putShort((short) format).putShort((short) 1)
        .putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16)
        .putInt(0x61746164).putInt(frames * 2).putShort((short) sample);
    return new MockMultipartFile("file", "recording.wav", "audio/wav", bytes.array());
  }

  protected java.net.http.HttpResponse<String> post(
      java.net.http.HttpClient client,
      tools.jackson.databind.ObjectMapper json,
      String base,
      String path,
      Object body,
      boolean csrf)
      throws Exception {
    return request(client, json, base, path, "POST", body, csrf);
  }

  protected java.net.http.HttpResponse<String> request(
      java.net.http.HttpClient client, tools.jackson.databind.ObjectMapper json, String base,
      String path, String method, Object body, boolean csrf) throws Exception {
    return vn.englishconversation.testing.HttpTestRequests.request(client, json, base, path, method, body, csrf);
  }

  protected java.net.http.HttpResponse<String> multipart(
      java.net.http.HttpClient browser, tools.jackson.databind.ObjectMapper json, String base,
      String path, boolean withCsrf, int frames) throws Exception {
    String boundary = "fixture-audio";
    var buffer = new java.io.ByteArrayOutputStream();
    buffer.writeBytes(("--" + boundary
        + "\r\nContent-Disposition: form-data; name=\"clientRequestId\"\r\n\r\nhttp-voice-fixture\r\n--"
        + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"capture.wav\""
        + "\r\nContent-Type: audio/wav\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    buffer.writeBytes(wave(frames, 900, 1).getBytes());
    buffer.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + path + "/audio"))
        .header("Content-Type", "multipart/form-data; boundary=" + boundary);
    if (withCsrf) {
      var csrf = browser.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "auth/csrf"))
          .GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
      var token = json.readTree(csrf.body());
      req.header(token.get("headerName").asString(), token.get("token").asString());
    }
    return browser.send(req.POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(buffer.toByteArray())).build(),
        java.net.http.HttpResponse.BodyHandlers.ofString());
  }

  @DynamicPropertySource
  protected static void isolated(DynamicPropertyRegistry properties) {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:(?:3307/phase[12]_auth_[0-9]{14}|13306/g6_test_[0-9a-f]{12})\\?connectionTimeZone=UTC"))
      throw new IllegalStateException("Use the isolated MySQL test script");
    properties.add("spring.datasource.url", () -> url);
    properties.add("app.account.avatar-directory", AVATAR_TEST_DIRECTORY::toString);
  }

  @TestConfiguration
  public static class FixtureConfig {
    @Bean
    java.time.Clock reviewClock() {
      return java.time.Clock.fixed(java.time.Instant.parse("2026-11-27T02:00:00Z"), java.time.ZoneOffset.UTC);
    }

    @Bean
    @Primary
    FixtureProvider fixtureProvider() {
      return new FixtureProvider();
    }
  }

  protected static final String CLEAN_REVIEW =
      """
{"summary":"Bạn đã gọi món rõ ràng.","strengths":["Dùng please để gọi món lịch sự."],
"goalAssessment":"Đã gọi món chính, chưa xác nhận đồ uống.","corrections":[],
"suggestions":[{"itemType":"VOCABULARY","expression":"receipt","meaningVi":"Biên lai",
"exampleEn":"Would you like a receipt?","partOfSpeech":"NOUN"}],"nextPractice":"Thử gọi thêm đồ uống và xác nhận đơn."}
""";

  protected AccountPrincipal owner() {
    return vn.englishconversation.testing.PracticeFixtures.learner(accounts);
  }

  protected CreatePracticeRequest draft() {
    return vn.englishconversation.testing.PracticeFixtures.draft(jdbc);
  }

}
