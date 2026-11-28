package vn.englishconversation.lesson;

import static org.junit.jupiter.api.Assertions.*;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("g2")
@EnabledIfEnvironmentVariable(named = "G2_MYSQL_TEST", matches = "true")
class LessonCatalogMysqlTest {
  @Value("${local.server.port}")
  int port;

  @Autowired JdbcTemplate jdbc;

  private final ObjectMapper json = new ObjectMapper();

  @DynamicPropertySource
  static void isolatedDatabase(DynamicPropertyRegistry properties) {
    String url = System.getenv("G2_TEST_DB_URL");
    if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:(3306|3307)/g2_test_[0-9a-f]{12}\\?connectionTimeZone=UTC"))
      throw new IllegalStateException("Use a dedicated G2 test schema");
    properties.add("spring.datasource.url", () -> url);
    properties.add("app.mail.enabled", () -> "false");
    properties.add("server.servlet.session.cookie.secure", () -> false);
    properties.add("app.admin-bootstrap.enabled", () -> "false");
  }

  @Test
  void seedAndSearchRespectUnicodeFiltersAndStablePaging() throws Exception {
    assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM lessons", Integer.class));
    assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM scenarios", Integer.class));
    assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM topics", Integer.class));
    assertEquals(401, new Browser().get("lessons").statusCode());
    var browser = learner();
    long topic = jdbc.queryForObject("SELECT id FROM topics WHERE code='TRAVEL'", Long.class);
    for (String term : new String[] {"dat phong", "ĐẶT PHÒNG"}) {
      var result = ok(browser.get("lessons?q=" + URLEncoder.encode(term, StandardCharsets.UTF_8)
          + "&topicId=" + topic + "&level=B1&objectiveCode=BOOK_A_ROOM"));
      assertEquals(1, result.get("total").asInt());
      assertEquals("Đặt phòng khách sạn", result.get("items").get(0).get("title").asString());
    }
    assertEquals(0, ok(browser.get("lessons?q=dat+phong&topicId=" + topic + "&level=A2")).get("total").asInt());
    assertEquals(0, ok(browser.get("lessons?q=%25")).get("total").asInt());
    assertEquals(400, browser.get("lessons?level=C2").statusCode());
    assertEquals(400, browser.get("lessons?size=51").statusCode());
    var ids = new HashSet<String>();
    for (int page = 0; page < 3; page++) {
      var result = ok(browser.get("lessons?size=3&page=" + page));
      assertEquals(8, result.get("total").asInt());
      for (var item : result.get("items")) assertTrue(ids.add(item.get("id").asString()));
    }
    assertEquals(8, ids.size());
    var shop = ok(browser.get("lessons/103"));
    var scenario = browser.get("scenarios/" + shop.get("scenarioId").asString() + "?level=B1");
    assertEquals(2, ok(scenario).get("roles").size());
    assertFalse(scenario.body().contains("aiInstruction"));
    assertFalse(scenario.body().contains("ai_instruction"));
    assertFalse(scenario.body().contains("Play the returns clerk"));
  }

  @Test
  void publicationConstraintsRejectMissingContentAndIncompatibleLevels() {
    assertThrows(DataIntegrityViolationException.class,
        () -> jdbc.update("UPDATE lessons SET level=NULL WHERE id=101"));
    assertThrows(DataIntegrityViolationException.class,
        () -> jdbc.update("UPDATE lessons SET level='B1' WHERE id=101"));
    var emptyObjective = assertThrows(UncategorizedSQLException.class,
        () -> jdbc.update("UPDATE lessons SET learning_objective='' WHERE id=101"));
    assertEquals(3819, emptyObjective.getSQLException().getErrorCode());
  }

  @Test
  void separateBookmarksRemainOwnerScopedAfterArchivingLessonOrScenario() throws Exception {
    var first = learner();
    var second = learner();
    String favorite = "lessons/105/bookmarks/FAVORITE";
    String later = "lessons/105/bookmarks/SAVE_FOR_LATER";
    assertEquals(403, first.call("PUT", favorite, null, false).statusCode());
    assertEquals(204, first.call("PUT", favorite, null, true).statusCode());
    assertEquals(204, first.call("PUT", favorite, null, true).statusCode());
    assertEquals(204, first.call("PUT", later, null, true).statusCode());
    assertEquals(204, second.call("PUT", favorite, null, true).statusCode());
    assertEquals(1, ok(first.get("lessons/saved?kind=FAVORITE")).get("total").asInt());
    assertEquals(0, ok(second.get("lessons/saved?kind=SAVE_FOR_LATER")).get("total").asInt());
    assertEquals(204, second.call("DELETE", later, null, true).statusCode());
    assertTrue(ok(first.get("lessons/105")).get("savedForLater").asBoolean());
    jdbc.update("UPDATE scenarios SET status='ARCHIVED' WHERE code='HOTEL_BOOKING'");
    try {
      assertTrue(ok(first.get("lessons/105")).get("scenarioId").isNull());
      jdbc.update("UPDATE lessons SET status='ARCHIVED' WHERE id=105");
      assertEquals(404, first.get("lessons/105").statusCode());
      assertEquals(404, first.call("PUT", favorite, null, true).statusCode());
      var item = ok(first.get("lessons/saved?kind=SAVE_FOR_LATER")).get("items").get(0);
      assertFalse(item.get("available").asBoolean());
      assertTrue(item.get("title").isNull());
      assertEquals(204, first.call("DELETE", favorite, null, true).statusCode());
      assertEquals(0, ok(first.get("lessons/saved?kind=FAVORITE")).get("total").asInt());
      assertEquals(1, ok(first.get("lessons/saved?kind=SAVE_FOR_LATER")).get("total").asInt());
      assertEquals(1, ok(second.get("lessons/saved?kind=FAVORITE")).get("total").asInt());
      assertEquals(204, first.call("DELETE", "learning/data", null, true).statusCode());
      assertEquals(0, ok(first.get("lessons/saved?kind=SAVE_FOR_LATER")).get("total").asInt());
      assertEquals(1, ok(second.get("lessons/saved?kind=FAVORITE")).get("total").asInt());
    } finally {
      jdbc.update("UPDATE lessons SET status='ACTIVE' WHERE id=105");
      jdbc.update("UPDATE scenarios SET status='ACTIVE' WHERE code='HOTEL_BOOKING'");
    }
  }

  private Browser learner() throws Exception {
    var browser = new Browser();
    var credentials = Map.of(
        "email", "catalog-" + UUID.randomUUID() + "@example.test",
        "password", "Synthetic catalog passphrase");
    assertEquals(201, browser.call("POST", "auth/register", credentials, true).statusCode());
    assertEquals(200, browser.call("POST", "auth/login", credentials, true).statusCode());
    return browser;
  }

  private JsonNode ok(HttpResponse<String> response) {
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body());
  }

  private class Browser {
    private final HttpClient client = HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

    HttpResponse<String> get(String path) throws Exception {
      return call("GET", path, null, false);
    }

    HttpResponse<String> call(String method, String path, Object body, boolean csrf) throws Exception {
      var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/" + path));
      if (csrf) {
        var token = ok(get("auth/csrf"));
        builder.header(token.get("headerName").asString(), token.get("token").asString());
      }
      if (body != null) builder.header("Content-Type", "application/json");
      return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
          : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }
}
