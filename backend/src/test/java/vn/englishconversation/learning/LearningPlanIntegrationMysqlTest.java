package vn.englishconversation.learning;


import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.englishconversation.conversation.dto.*;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class LearningPlanIntegrationMysqlTest extends FullSystemMysqlFixture {

  @Test
  void learningPlanGuardsVersionsSourcesAndDeletion() throws Exception {
    var browser = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var second = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var json = new tools.jackson.databind.ObjectMapper();
    String base = "http://127.0.0.1:" + port + "/api/v1/";
    String plans = "learning/plans";
    String password = "Synthetic planner password";
    var a = accounts.register("plan-a-" + java.util.UUID.randomUUID() + "@example.test", password);
    var b = accounts.register("plan-b-" + java.util.UUID.randomUUID() + "@example.test", password);
    assertEquals(200, post(browser, json, base, "auth/login",
        java.util.Map.of("email", a.email(), "password", password), true).statusCode());
    assertEquals(200, post(second, json, base, "auth/login",
        java.util.Map.of("email", b.email(), "password", password), true).statusCode());
    var settings = java.util.Map.of("goal", "Practice introductions", "focusSkill", "CONVERSATION",
        "startDate", "2026-12-01", "days", 7);
    var create = java.util.Map.of("settings", settings);
    var made = post(browser, json, base, plans, create, true);
    assertEquals(201, made.statusCode(), made.body());
    var plan = json.readTree(made.body()).get("plan");
    String path = plans + "/" + plan.get("id").asString();
    assertEquals("2026-12-07", plan.get("endDate").asString());
    assertEquals(409, post(browser, json, base, plans, create, true).statusCode());
    assertEquals(404, request(second, json, base, path, "GET", null, false).statusCode());

    long aId = Long.parseLong(a.id());
    long bId = Long.parseLong(b.id());
    for (long owner : List.of(aId, bId)) {
      jdbc.update("""
          INSERT INTO review_items(user_id,item_type,expression,meaning_vi,status,created_at,updated_at)
          VALUES (?,'PHRASE','Hello again','Chào lại','NEEDS_REVIEW',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
          """, owner);
    }
    long ownedCard = jdbc.queryForObject("SELECT id FROM review_items WHERE user_id=?", Long.class, aId);
    long otherCard = jdbc.queryForObject("SELECT id FROM review_items WHERE user_id=?", Long.class, bId);
    var exactCard = request(browser, json, base, "learning/notebook/" + ownedCard, "GET", null, false);
    assertEquals(200, exactCard.statusCode(), exactCard.body());
    assertEquals(Long.toString(ownedCard), json.readTree(exactCard.body()).get("id").asString());
    assertEquals(404, request(second, json, base, "learning/notebook/" + ownedCard, "GET", null, false).statusCode());
    var lesson = java.util.Map.of("type", "LESSON", "id", 101);
    assertEquals(422, post(browser, json, base, path + "/items",
        java.util.Map.of("version", 1, "dueDate", "2026-12-08", "position", 1, "source", lesson), true).statusCode());
    assertEquals(404, post(browser, json, base, path + "/items", java.util.Map.of("version", 1,
        "dueDate", "2026-12-01", "position", 1, "source", java.util.Map.of("type", "REVIEW_ITEM", "id", otherCard)), true).statusCode());
    var added = post(browser, json, base, path + "/items", java.util.Map.of("version", 1,
        "dueDate", "2026-12-01", "position", 1, "source", java.util.Map.of("type", "REVIEW_ITEM", "id", ownedCard),
        "recommended", true), true);
    assertEquals(201, added.statusCode(), added.body());
    var item = json.readTree(added.body()).get("items").get(0);
    assertEquals("REVIEW_NEEDED", item.get("reasonCode").asString());
    String itemPath = path + "/items/" + item.get("id").asString();
    assertEquals(409, request(browser, json, base, path, "PUT",
        java.util.Map.of("version", 1, "settings", settings), true).statusCode());

    assertEquals(204, request(browser, json, base, "learning/notebook/" + ownedCard, "DELETE", null, true).statusCode());
    var lost = json.readTree(request(browser, json, base, path, "GET", null, false).body()).get("items").get(0);
    assertTrue(lost.get("reviewItemId").isNull());
    assertFalse(lost.get("available").asBoolean());
    assertEquals("Hello again", lost.get("title").asString());
    assertEquals("PLANNED", lost.get("status").asString());
    var replaced = request(browser, json, base, itemPath, "PUT",
        java.util.Map.of("version", 2, "dueDate", "2026-12-02", "position", 1, "source", lesson), true);
    assertEquals(200, replaced.statusCode(), replaced.body());
    var result = post(browser, json, base, itemPath + "/finish",
        java.util.Map.of("version", 3, "result", "COMPLETED"), true);
    assertEquals(200, result.statusCode(), result.body());
    assertFalse(json.readTree(result.body()).get("items").get(0).get("completedAt").isNull());
    String oldStatus = jdbc.queryForObject("SELECT status FROM lessons WHERE id=101", String.class);
    try {
      jdbc.update("UPDATE lessons SET status='ARCHIVED' WHERE id=101");
      var hidden = json.readTree(request(browser, json, base, path, "GET", null, false).body()).get("items").get(0);
      assertFalse(hidden.get("available").asBoolean());
      assertEquals("COMPLETED", hidden.get("status").asString());
      assertFalse(hidden.get("title").asString().isBlank());
    } finally {
      jdbc.update("UPDATE lessons SET status=? WHERE id=101", oldStatus);
    }
    var successor = post(browser, json, base, plans, java.util.Map.of("settings", settings,
        "replaceId", plan.get("id").asLong(), "version", 4), true);
    assertEquals(201, successor.statusCode(), successor.body());
    assertEquals("ARCHIVED", json.readTree(request(browser, json, base, path, "GET", null, false).body())
        .get("plan").get("status").asString());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM learning_plans WHERE user_id=? AND status='ACTIVE'", Integer.class, aId));
    String nextPath = plans + "/" + json.readTree(successor.body()).get("plan").get("id").asString();
    var tab = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    assertEquals(200, post(tab, json, base, "auth/login",
        java.util.Map.of("email", a.email(), "password", password), true).statusCode());
    var sameVersion = java.util.Map.of("version", 1, "settings", settings);
    var left = CompletableFuture.supplyAsync(() -> planEditStatus(browser, json, base, nextPath, sameVersion));
    var right = CompletableFuture.supplyAsync(() -> planEditStatus(tab, json, base, nextPath, sameVersion));
    assertEquals(List.of(200, 409), java.util.stream.Stream.of(left.join(), right.join()).sorted().toList());
    var firstItem = post(browser, json, base, nextPath + "/items", java.util.Map.of("version", 2,
        "dueDate", "2026-12-01", "position", 1, "source", lesson), true);
    assertEquals(201, firstItem.statusCode(), firstItem.body());
    String firstId = json.readTree(firstItem.body()).get("items").get(0).get("id").asString();
    var secondItem = post(browser, json, base, nextPath + "/items", java.util.Map.of("version", 3,
        "dueDate", "2026-12-01", "position", 1, "source", java.util.Map.of("type", "LESSON", "id", 102)), true);
    assertEquals(201, secondItem.statusCode(), secondItem.body());
    assertEquals(firstId, json.readTree(secondItem.body()).get("items").get(1).get("id").asString());
    var reordered = request(browser, json, base, nextPath + "/items/" + firstId, "PUT",
        java.util.Map.of("version", 4, "dueDate", "2026-12-01", "position", 1), true);
    assertEquals(200, reordered.statusCode(), reordered.body());
    assertEquals(firstId, json.readTree(reordered.body()).get("items").get(0).get("id").asString());
    var skipped = post(browser, json, base, nextPath + "/items/" + firstId + "/finish",
        java.util.Map.of("version", 5, "result", "SKIPPED"), true);
    assertEquals(200, skipped.statusCode(), skipped.body());
    assertEquals("SKIPPED", json.readTree(skipped.body()).get("items").get(0).get("status").asString());
    assertEquals(204, request(browser, json, base, "learning/data", "DELETE", null, true).statusCode());
    assertEquals(0, json.readTree(request(browser, json, base, plans, "GET", null, false).body()).size());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM learning_plan_items WHERE plan_id=?", Integer.class, plan.get("id").asLong()));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM review_items WHERE id=?", Integer.class, otherCard));
  }

  private int planEditStatus(java.net.http.HttpClient browser, tools.jackson.databind.ObjectMapper json,
      String base, String path, Object body) {
    try {
      return request(browser, json, base, path, "PUT", body, true).statusCode();
    } catch (Exception failure) {
      throw new CompletionException(failure);
    }
  }
}
