package vn.englishconversation.lesson;

import static org.junit.jupiter.api.Assertions.*;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("g3")
@EnabledIfEnvironmentVariable(named = "G3_MYSQL_TEST", matches = "true")
class LessonMaterialsMysqlTest {
  private static final String NOTEBOOK = "learning/notebook";
  private final ObjectMapper json = new ObjectMapper();
  @Value("${local.server.port}") int port;
  @Autowired JdbcTemplate jdbc;

  @DynamicPropertySource
  static void isolatedDatabase(DynamicPropertyRegistry properties) {
    String url = System.getenv("G3_TEST_DB_URL");
    if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/g3_test_[0-9a-f]{12}\\?connectionTimeZone=UTC"))
      throw new IllegalStateException("Use a dedicated G3 test schema");
    properties.add("spring.datasource.url", () -> url);
    properties.add("app.mail.enabled", () -> false);
    properties.add("app.admin-bootstrap.enabled", () -> false);
    properties.add("server.servlet.session.cookie.secure", () -> false);
  }

  @Test
  void eightLessonsHaveCompleteMaterialAndHiddenLessonsReturn404() throws Exception {
    assertEquals(401, new Learner().get("lessons/101/materials").statusCode());
    var owner = learner();
    for (int id = 101; id <= 108; id++) {
      var materials = ok(owner.get("lessons/" + id + "/materials"));
      assertEquals(5, materials.get("items").size());
      int words = 0;
      for (var item : materials.get("items")) {
        if ("WORD".equals(item.get("kind").asString())) {
          assertFalse(item.get("ipa").asString().isBlank());
          words++;
        }
        for (String field : new String[] {"expression", "meaningVi", "exampleEn", "exampleVi"})
          assertFalse(item.get(field).asString().isBlank());
      }
      assertEquals(3, words);
      assertEquals(1, materials.get("dialogues").size());
      var lines = materials.get("dialogues").get(0).get("lines");
      assertTrue(lines.size() >= 4 && lines.size() <= 6);
      for (int i = 0; i < lines.size(); i++) {
        assertEquals(i + 1, lines.get(i).get("line_no").asInt());
        assertFalse(lines.get(i).get("text_en").asString().isBlank());
      }
    }
    jdbc.update("UPDATE lessons SET status='ARCHIVED' WHERE id=101");
    try {
      assertEquals(404, owner.get("lessons/101/materials").statusCode());
    } finally {
      jdbc.update("UPDATE lessons SET status='ACTIVE' WHERE id=101");
    }
  }

  @Test
  void notebookOwnsSnapshotsAndReviewsRemainIdempotent() throws Exception {
    var owner = learner();
    var other = learner();
    var material = ok(owner.get("lessons/101/materials")).get("items").get(0);
    String source = material.get("id").asString();
    String save = NOTEBOOK + "/from-language-item/" + source;
    assertEquals(403, owner.send("POST", save, null, null).join().statusCode());
    var first = owner.send("POST", save, null, owner.csrf());
    var second = owner.send("POST", save, null, owner.csrf());
    var card = ok(first.join());
    assertEquals(card, ok(second.join()));
    assertEquals(material.get("ipa"), card.get("ipa"));
    assertEquals(material.get("exampleEn"), card.get("exampleEn"));
    assertEquals(1, ok(owner.get(NOTEBOOK)).get("total").asInt());
    assertEquals(0, ok(other.get(NOTEBOOK)).get("total").asInt());

    String key = card.get("id").asString();
    String cardPath = NOTEBOOK + "/" + key;
    String attempts = cardPath + "/attempts";
    assertEquals(404, other.get(attempts).statusCode());
    assertEquals(404, other.change("POST", cardPath + "/quiz", null).statusCode());
    assertEquals(404, other.change("DELETE", cardPath, null).statusCode());
    var flash = Map.of("mode", "FLASHCARD", "result", "REMEMBERED", "clientRequestId", "same-request");
    var marked = ok(owner.change("POST", attempts, flash));
    assertEquals(marked, ok(owner.change("POST", attempts, flash)));
    assertEquals(409, owner.change("POST", attempts,
        Map.of("mode", "FLASHCARD", "result", "NEEDS_REVIEW", "clientRequestId", "same-request")).statusCode());

    for (boolean expected : new boolean[] {true, false}) {
      var question = ok(owner.change("POST", cardPath + "/quiz", null));
      assertFalse(question.has("correctOptionId"));
      assertEquals(3, question.get("options").size());
      JsonNode choice = null;
      for (var option : question.get("options")) {
        if (expected == option.get("meaningVi").equals(card.get("meaningVi"))) {
          choice = option;
          break;
        }
      }
      assertNotNull(choice);
      var input = Map.of("mode", "QUICK_QUIZ", "clientRequestId", question.get("id").asString(),
          "optionId", choice.get("id").asString());
      var result = ok(owner.change("POST", attempts, input));
      assertEquals(expected, result.get("correct").asBoolean());
      assertEquals(result, ok(owner.change("POST", attempts, input)));
    }
    assertEquals(3, ok(owner.get(attempts)).get("total").asInt());
    assertEquals(1, ok(owner.get(NOTEBOOK + "?status=REMEMBERED")).get("total").asInt());
    assertEquals(409, owner.change("POST", attempts,
        Map.of("mode", "QUICK_QUIZ", "clientRequestId", "unknown", "optionId", "unknown")).statusCode());
    jdbc.update("UPDATE language_items SET status='ARCHIVED' WHERE kind='WORD'");
    try {
      var unavailable = owner.change("POST", cardPath + "/quiz", null);
      assertEquals(409, unavailable.statusCode());
      assertTrue(unavailable.body().contains("QUIZ_UNAVAILABLE"));
      assertEquals(1, ok(owner.get(NOTEBOOK)).get("total").asInt());
    } finally {
      jdbc.update("UPDATE language_items SET status='ACTIVE' WHERE kind='WORD'");
    }
    jdbc.update("""
        INSERT INTO language_items(lesson_id,kind,expression,ipa,meaning_vi,example_en,sort_order,status,created_at,updated_at)
        VALUES (101,'WORD','notebook test','/tɛst/','bản thử','This is a test.',9,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """);
    long temporary = jdbc.queryForObject(
        "SELECT MAX(id) FROM language_items WHERE expression='notebook test'", Long.class);
    var copy = ok(owner.change("POST", NOTEBOOK + "/from-language-item/" + temporary, null));
    jdbc.update("DELETE FROM language_items WHERE id=?", temporary);
    var detached = ok(owner.get(NOTEBOOK)).get("items").get(0);
    assertEquals(copy.get("id"), detached.get("id"));
    assertEquals(copy.get("meaningVi"), detached.get("meaningVi"));
    assertTrue(detached.get("sourceLanguageItemId").isNull());

    assertEquals(204, owner.change("DELETE", cardPath, null).statusCode());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM review_attempts WHERE review_item_id=?", Integer.class, key));
    assertEquals(204, owner.change("PUT", "lessons/101/bookmarks/FAVORITE", null).statusCode());
    var identity = ok(owner.get("auth/me"));
    var profile = ok(owner.get("profile/me"));
    var schedule = ok(owner.get("profile/me/practice-schedule"));
    assertEquals(204, owner.change("DELETE", "learning/data", null).statusCode());
    assertEquals(0, ok(owner.get(NOTEBOOK)).get("total").asInt());
    assertEquals(0, ok(owner.get("lessons/saved?kind=FAVORITE")).get("total").asInt());
    assertEquals(identity, ok(owner.get("auth/me")));
    assertEquals(profile, ok(owner.get("profile/me")));
    assertEquals(schedule, ok(owner.get("profile/me/practice-schedule")));
  }

  private Learner learner() throws Exception {
    var learner = new Learner();
    var login = Map.of("email", "g3-" + UUID.randomUUID() + "@example.test", "password", "Synthetic notebook passphrase");
    assertEquals(201, learner.change("POST", "auth/register", login).statusCode());
    assertEquals(200, learner.change("POST", "auth/login", login).statusCode());
    return learner;
  }

  private JsonNode ok(HttpResponse<String> response) {
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body());
  }

  private class Learner {
    private final HttpClient http = HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

    HttpResponse<String> get(String path) {
      return send("GET", path, null, null).join();
    }

    JsonNode csrf() {
      return ok(get("auth/csrf"));
    }

    HttpResponse<String> change(String method, String path, Object body) {
      return send(method, path, body, csrf()).join();
    }

    CompletableFuture<HttpResponse<String>> send(String method, String path, Object body, JsonNode csrf) {
      var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/" + path));
      if (csrf != null) request.header(csrf.get("headerName").asString(), csrf.get("token").asString());
      if (body != null) request.header("Content-Type", "application/json");
      return http.sendAsync(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
          : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
  }
}
