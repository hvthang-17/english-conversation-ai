package vn.englishconversation.listening.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.common.exception.ApiException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("g4")
@EnabledIfEnvironmentVariable(named = "G4_MYSQL_TEST", matches = "true")
class ListeningMysqlTest {
  private static final String ATTEMPTS = "listening/attempts";
  private static final String START = "listening/exercises/201/attempts";
  private static final String PUBLISH = "admin/listening-exercises/";
  private final ObjectMapper json = new ObjectMapper();
  @Value("${local.server.port}") int port;
  @Autowired JdbcTemplate db;
  @MockitoBean AzureListeningSpeech speech;

  @DynamicPropertySource
  static void isolated(DynamicPropertyRegistry registry) throws IOException {
    String url = System.getenv("G4_TEST_DB_URL");
    if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:(3306|13306)/g4_test_[0-9a-f]{12}\\?connectionTimeZone=UTC"))
      throw new IllegalStateException("Use a dedicated G4 test schema");
    var folder = Files.createTempDirectory(Path.of("target"), "listening-test-").toAbsolutePath();
    registry.add("spring.datasource.url", () -> url);
    registry.add("app.listening.audio-directory", folder::toString);
    registry.add("app.mail.enabled", () -> false);
    registry.add("app.admin-bootstrap.enabled", () -> false);
    registry.add("server.servlet.session.cookie.secure", () -> false);
  }

  @Test
  void publishesValidatedAudioAndSupportsPrivateVersionedAttempts() throws Exception {
    assertEquals(8, db.queryForObject("SELECT COUNT(*) FROM listening_exercises WHERE status='DRAFT'", Integer.class));
    assertEquals(401, new Browser().get("lessons/101/listening").statusCode());
    assertEquals(401, new Browser().get("listening/exercises").statusCode());
    var owner = new Browser("learner");
    var secondDevice = new Browser();
    secondDevice.login(owner.credentials);
    var other = new Browser("other");
    var adminSignup = new Browser("admin");
    db.update("UPDATE users SET role='ADMIN' WHERE email=?", adminSignup.credentials.get("email"));
    var admin = new Browser();
    admin.login(adminSignup.credentials);
    assertEquals(403, owner.mutate("POST", PUBLISH + "201/publish", null).statusCode());
    assertEquals(0, ok(owner.get("lessons/101/listening")).size());
    assertEquals(0, ok(owner.get("listening/exercises")).get("total").asInt());
    assertEquals(404, owner.mutate("POST", START, Map.of("clientRequestId", "on-draft")).statusCode());

    when(speech.synthesize(anyString())).thenThrow(new ApiException(503, "LISTENING_TTS_UNAVAILABLE", "Fixture"))
        .thenReturn(new byte[64], ListeningRulesTest.fixtureFrames());
    assertEquals(503, admin.mutate("POST", PUBLISH + "201/publish", null).statusCode());
    assertEquals(502, admin.mutate("POST", PUBLISH + "201/publish", null).statusCode());
    assertEquals(0, ok(owner.get("lessons/101/listening")).size());
    assertEquals("ACTIVE", ok(admin.mutate("POST", PUBLISH + "201/publish", null)).get("status").asString());
    var catalog = ok(owner.get("listening/exercises?size=1"));
    assertEquals(1, catalog.get("total").asInt());
    assertEquals("201", catalog.get("items").get(0).get("id").asString());
    assertFalse(catalog.get("items").get(0).has("transcriptEn"));
    assertFalse(catalog.get("items").get(0).has("questions"));
    assertEquals(0, ok(owner.get(ATTEMPTS)).get("total").asInt());
    assertEquals(0, ok(owner.get("listening/exercises?size=1&page=1")).get("items").size());
    assertEquals(400, owner.get("listening/exercises?size=51").statusCode());
    ok(admin.mutate("POST", PUBLISH + "201/publish", null));
    verify(speech, times(3)).synthesize(anyString());

    assertEquals(403, owner.send("POST", START, Map.of("clientRequestId", "csrf-less"), null).join().statusCode());
    var first = owner.send("POST", START, Map.of("clientRequestId", "first"), owner.csrf());
    var concurrent = secondDevice.send("POST", START, Map.of("clientRequestId", "concurrent"), secondDevice.csrf());
    var opened = ok(first.join());
    assertEquals(opened.get("id"), ok(concurrent.join()).get("id"));
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM listening_attempts WHERE exercise_id=201", Integer.class));
    var path = ATTEMPTS + "/" + opened.get("id").asString();
    assertTrue(opened.get("result").isNull());
    var questions = opened.get("questions").toString();
    for (String field : new String[] {"accepted", "answerKey", "explanationVi", "transcriptEn", "textEn"})
      assertFalse(questions.contains(field), field);
    assertEquals(404, other.get(path).statusCode());
    assertEquals(404, other.get(path + "/audio").statusCode());
    assertEquals(404, other.mutate("POST", path + "/submit", answers(0, Map.of())).statusCode());
    var bytes = owner.send("GET", path + "/audio", null, null, HttpResponse.BodyHandlers.ofByteArray()).join();
    assertEquals(200, bytes.statusCode());
    assertArrayEquals(ListeningRulesTest.fixtureFrames(), bytes.body());

    var choices = Map.of("q1", "B", "q2", " Hue. ", "q3", "Incorrect");
    assertEquals(400, owner.mutate("PUT", path + "/draft", answers(0, Map.of("foreign","A"))).statusCode());
    assertEquals(1, ok(owner.mutate("PUT", path + "/draft", answers(0, choices))).get("draftVersion").asInt());
    assertEquals(409, secondDevice.mutate("PUT", path + "/draft", answers(0, Map.of())).statusCode());
    assertEquals(json.valueToTree(choices), ok(secondDevice.get(path)).get("answers"));
    assertEquals(409, secondDevice.mutate("POST", path + "/submit", answers(0, choices)).statusCode());
    var submitted = answers(1, choices);
    var one = owner.send("POST", path + "/submit", submitted, owner.csrf());
    var two = secondDevice.send("POST", path + "/submit", submitted, secondDevice.csrf());
    var result = ok(one.join());
    assertEquals(result, ok(two.join()));
    assertEquals(2, result.get("result").get("score").asInt());
    assertEquals(3, result.get("result").get("maxScore").asInt());
    assertTrue(result.get("result").get("transcriptEn").asString().contains("Anna"));
    assertEquals(409, owner.mutate("POST", path + "/submit", answers(1, Map.of())).statusCode());
    assertEquals(409, owner.mutate("PUT", path + "/draft", submitted).statusCode());
    assertEquals(0, ok(other.get(ATTEMPTS)).get("total").asInt());

    var retake = ok(owner.mutate("POST", START, Map.of("clientRequestId", "retake")));
    assertTrue(retake.get("result").isNull());
    assertNotEquals(opened.get("id"), retake.get("id"));
    var retakePath = ATTEMPTS + "/" + retake.get("id").asString();
    db.update("""
        INSERT INTO listening_exercises(id,lesson_id,title,instructions,tts_voice_key,transcript_en,questions_json,created_at,updated_at)
        SELECT 250,lesson_id,'New version',instructions,tts_voice_key,transcript_en,questions_json,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
        FROM listening_exercises WHERE id=201
        """);
    ok(admin.mutate("POST", PUBLISH + "250/publish", null));
    assertEquals("250", ok(owner.get("lessons/101/listening")).get(0).get("id").asString());
    assertEquals("250", ok(other.get("listening/exercises")).get("items").get(0).get("id").asString());
    assertEquals(404, other.mutate("POST", START, Map.of("clientRequestId", "old-version")).statusCode());
    assertEquals(200, secondDevice.get(retakePath + "/audio").statusCode());
    assertEquals(result, ok(owner.get(path)));
    assertEquals(204, owner.mutate("POST", retakePath + "/abandon", null).statusCode());
    assertEquals("ABANDONED", ok(owner.get(retakePath)).get("status").asString());
    verify(speech, times(4)).synthesize(anyString());

    assertEquals(204, owner.mutate("DELETE", "learning/data", null).statusCode());
    assertEquals(0, ok(owner.get(ATTEMPTS)).get("total").asInt());
    assertEquals(200, owner.get("auth/me").statusCode());
  }

  private static Map<String, Object> answers(int version, Map<String, ?> values) {
    return Map.of("draftVersion", version, "answers", values);
  }

  private JsonNode ok(HttpResponse<String> response) {
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body());
  }

  private class Browser {
    private final HttpClient http = HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    private Map<String,String> credentials;

    Browser() {}

    Browser(String name) {
      var login = Map.of("email", name + "-" + UUID.randomUUID() + "@example.test", "password", "Synthetic G4 test passphrase");
      assertEquals(201, mutate("POST", "auth/register", login).statusCode());
      login(login);
    }

    void login(Map<String,String> login) {
      assertEquals(200, mutate("POST", "auth/login", login).statusCode());
      credentials = login;
    }

    JsonNode csrf() { return ok(get("auth/csrf")); }
    HttpResponse<String> get(String path) { return send("GET", path, null, null).join(); }
    HttpResponse<String> mutate(String method, String path, Object body) { return send(method, path, body, csrf()).join(); }

    CompletableFuture<HttpResponse<String>> send(String method, String path, Object body, JsonNode csrf) {
      return send(method, path, body, csrf, HttpResponse.BodyHandlers.ofString());
    }

    <T> CompletableFuture<HttpResponse<T>> send(String method, String path, Object body, JsonNode csrf,
        HttpResponse.BodyHandler<T> handler) {
      var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/" + path));
      if (csrf != null) request.header(csrf.get("headerName").asString(), csrf.get("token").asString());
      if (body != null) request.header("Content-Type", "application/json");
      return http.sendAsync(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
          : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), handler);
    }
  }
}
