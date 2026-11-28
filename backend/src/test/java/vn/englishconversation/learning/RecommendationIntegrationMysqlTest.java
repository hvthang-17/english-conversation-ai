package vn.englishconversation.learning;


import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.englishconversation.conversation.dto.*;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class RecommendationIntegrationMysqlTest extends FullSystemMysqlFixture {

  @Test
  void difficultyRequiresThreeComparableExercisesAndUsesExactThresholds() throws Exception {
    var browser = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var json = new tools.jackson.databind.ObjectMapper();
    String base = "http://127.0.0.1:" + port + "/api/v1/";
    String path = "learning/recommendations/difficulty";
    assertEquals(401, request(browser, json, base, path, "GET", null, false).statusCode());
    String password = "Synthetic difficulty password";
    var account = accounts.register("difficulty-" + java.util.UUID.randomUUID() + "@example.test", password);
    long owner = Long.parseLong(account.id());
    assertEquals(200, post(browser, json, base, "auth/login",
        java.util.Map.of("email", account.email(), "password", password), true).statusCode());
    jdbc.update("UPDATE learner_profiles SET selected_level='A2' WHERE user_id=?", owner);
    var snapshots = jdbc.queryForList("""
        SELECT id,status,questions_json FROM listening_exercises WHERE id IN (201,202,204,203,205,206)
        """);
    String matching = java.util.Collections.nCopies(5, "{\"type\":\"DICTATION\"}").stream()
        .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    try {
      jdbc.update("""
          UPDATE listening_exercises SET status='ACTIVE',questions_json=?
          WHERE id IN (201,202,204,203,205,206)
          """, matching);
      saveListeningScore(owner, 201, "a-1", 4, 1);
      saveListeningScore(owner, 201, "a-2", 4, 1);
      saveListeningScore(owner, 202, "a-3", 4, 1);
      assertTrue(difficultyResponse(browser, json, base, path).isNull());
      saveListeningScore(owner, 204, "a-4", 4, 2);
      assertTrue(difficultyResponse(browser, json, base, path).isNull());
      saveListeningScore(owner, 204, "a-5", 4, 1);
      jdbc.update("UPDATE listening_exercises SET questions_json=? WHERE id=204", matching.replace("DICTATION", "FILL_BLANK"));
      assertTrue(difficultyResponse(browser, json, base, path).isNull());
      jdbc.update("UPDATE listening_exercises SET questions_json=? WHERE id=204", matching);
      assertEquals("B1", difficultyResponse(browser, json, base, path).get("suggestedLevel").asString());
      assertEquals("A2", jdbc.queryForObject("SELECT selected_level FROM learner_profiles WHERE user_id=?", String.class, owner));

      jdbc.update("UPDATE learner_profiles SET selected_level='B1' WHERE user_id=?", owner);
      saveListeningScore(owner, 203, "b-1", 2, 1);
      saveListeningScore(owner, 205, "b-2", 2, 1);
      saveListeningScore(owner, 206, "b-3", 3, 1);
      assertTrue(difficultyResponse(browser, json, base, path).isNull());
      saveListeningScore(owner, 206, "b-4", 2, 1);
      assertEquals("A2", difficultyResponse(browser, json, base, path).get("suggestedLevel").asString());
      assertEquals(204, request(browser, json, base, "learning/data", "DELETE", null, true).statusCode());
      assertTrue(difficultyResponse(browser, json, base, path).isNull());
      assertEquals("B1", jdbc.queryForObject("SELECT selected_level FROM learner_profiles WHERE user_id=?", String.class, owner));
    } finally {
      for (var row : snapshots) {
        jdbc.update("UPDATE listening_exercises SET status=?,questions_json=? WHERE id=?",
            row.get("status"), row.get("questions_json"), row.get("id"));
      }
    }
  }

  @Test
  void recommendationsUseOwnedEvidenceAndAvailableCatalogInStableOrder() throws Exception {
    var json = new tools.jackson.databind.ObjectMapper();
    String base = "http://127.0.0.1:" + port + "/api/v1/";
    String path = "learning/recommendations";
    var first = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var second = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    assertEquals(401, request(first, json, base, path, "GET", null, false).statusCode());
    String suffix = java.util.UUID.randomUUID().toString();
    String password = "Synthetic recommendation password";
    var a = accounts.register("recommend-a-" + suffix + "@example.test", password);
    var b = accounts.register("recommend-b-" + suffix + "@example.test", password);
    long owner = Long.parseLong(a.id());
    long other = Long.parseLong(b.id());
    assertEquals(200, post(first, json, base, "auth/login",
        java.util.Map.of("email", a.email(), "password", password), true).statusCode());
    assertEquals(200, post(second, json, base, "auth/login",
        java.util.Map.of("email", b.email(), "password", password), true).statusCode());
    jdbc.update("UPDATE learner_profiles SET selected_level='A2' WHERE user_id IN (?,?)", owner, other);
    var fresh = request(first, json, base, path, "GET", null, false);
    assertEquals(200, fresh.statusCode(), fresh.body());
    assertEquals("LEVEL_MATCH", json.readTree(fresh.body()).get(0).get("reasonCode").asString());
    for (var item : json.readTree(fresh.body())) assertEquals("A2", item.get("level").asString());

    jdbc.update("""
        INSERT INTO user_topic_interests(user_id,topic_id,created_at)
        SELECT ?,topic_id,UTC_TIMESTAMP(6) FROM lessons WHERE id=104
        """, owner);
    jdbc.update("""
        INSERT INTO user_topic_interests(user_id,topic_id,created_at)
        SELECT ?,topic_id,UTC_TIMESTAMP(6) FROM lessons WHERE id=102
        """, other);
    var aTopics = json.readTree(request(first, json, base, path, "GET", null, false).body());
    var bTopics = json.readTree(request(second, json, base, path, "GET", null, false).body());
    assertEquals("104", aTopics.get(0).get("id").asString());
    assertEquals("102", bTopics.get(0).get("id").asString());
    assertEquals("TOPIC_INTEREST", aTopics.get(0).get("reasonCode").asString());
    String focused = request(first, json, base, path + "?focus=PRONUNCIATION", "GET", null, false).body();
    assertTrue(focused.contains("FOCUS_MATCH"), focused);

    for (var card : List.of("Needs practice", "Already remembered")) {
      jdbc.update("""
          INSERT INTO review_items(user_id,item_type,expression,meaning_vi,status,created_at,updated_at)
          VALUES (?,'PHRASE',?,'Mẫu thử',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
          """, owner, card, card.equals("Needs practice") ? "NEEDS_REVIEW" : "REMEMBERED");
    }
    String oldExercise = jdbc.queryForObject("SELECT status FROM listening_exercises WHERE id=201", String.class);
    String oldLesson = jdbc.queryForObject("SELECT status FROM lessons WHERE id=102", String.class);
    try {
      jdbc.update("UPDATE listening_exercises SET status='ACTIVE' WHERE id=201");
      jdbc.update("UPDATE lessons SET status='ARCHIVED' WHERE id=102");
      for (int i = 0; i < 2; i++) {
        jdbc.update("""
            INSERT INTO listening_attempts(user_id,exercise_id,client_request_id,status,score,max_score,
              answers_json,started_at,submitted_at)
            VALUES (?,201,?,'SUBMITTED',1,4,JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """, owner, "recommend-" + i);
      }
      var suggestions = request(first, json, base, path, "GET", null, false);
      assertEquals(200, suggestions.statusCode(), suggestions.body());
      var data = json.readTree(suggestions.body());
      assertEquals("REVIEW_NEEDED", data.get(0).get("reasonCode").asString());
      assertEquals("Needs practice", data.get(0).get("title").asString());
      assertEquals("SKILL_GAP", data.get(1).get("reasonCode").asString());
      assertEquals("101", data.get(1).get("id").asString());
      assertFalse(suggestions.body().contains("Already remembered"));
      assertFalse(suggestions.body().contains("\"id\":\"102\""));
      String otherBody = request(second, json, base, path, "GET", null, false).body();
      assertFalse(otherBody.contains("Needs practice"));
      assertFalse(otherBody.contains("SKILL_GAP"));
      assertEquals(suggestions.body(), request(first, json, base, path, "GET", null, false).body());
    } finally {
      jdbc.update("UPDATE listening_exercises SET status=? WHERE id=201", oldExercise);
      jdbc.update("UPDATE lessons SET status=? WHERE id=102", oldLesson);
    }
  }

  private tools.jackson.databind.JsonNode difficultyResponse(java.net.http.HttpClient browser,
      tools.jackson.databind.ObjectMapper json, String base, String path) throws Exception {
    var response = request(browser, json, base, path, "GET", null, false);
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).get("suggestion");
  }

  private void saveListeningScore(long owner, int exercise, String key, int score, int gradingVersion) {
    jdbc.update("""
        INSERT INTO listening_attempts(user_id,exercise_id,client_request_id,status,score,max_score,
          answers_json,started_at,submitted_at,grading_version)
        VALUES(?,?,?,'SUBMITTED',?,5,JSON_ARRAY(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?)
        """, owner, exercise, key, score, gradingVersion);
  }
}
