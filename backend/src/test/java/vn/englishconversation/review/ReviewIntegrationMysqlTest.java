package vn.englishconversation.review;


import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.conversation.dto.*;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class ReviewIntegrationMysqlTest extends FullSystemMysqlFixture {

  @Test
  void reviewAdaptersShareReplayRulesAndPreserveLegacyRowsAndSnapshots() throws Exception {
    var browser = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var outsider = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var json = new tools.jackson.databind.ObjectMapper();
    var base = "http://127.0.0.1:" + port + "/api/v1/";
    String password = "Synthetic BR08 review password";
    var account = accounts.register("review-owner-" + java.util.UUID.randomUUID() + "@example.test", password);
    var other = accounts.register("review-other-" + java.util.UUID.randomUUID() + "@example.test", password);
    assertEquals(200, post(browser, json, base, "auth/login", java.util.Map.of("email", account.email(), "password", password), true).statusCode());
    assertEquals(200, post(outsider, json, base, "auth/login", java.util.Map.of("email", other.email(), "password", password), true).statusCode());
    long lesson = jdbc.queryForObject("""
        SELECT l.id FROM lessons l JOIN topics t ON t.id=l.topic_id
        WHERE l.status='ACTIVE' AND t.is_active=TRUE ORDER BY l.id LIMIT 1
        """, Long.class);
    long source = new vn.englishconversation.common.repository.JdbcStore(jdbc).insert("""
        INSERT INTO language_items(lesson_id,kind,expression,ipa,meaning_vi,example_en,part_of_speech,sort_order,status,created_at,updated_at)
        VALUES (?,'WORD','review snapshot','/rɪˈvjuː/','ôn tập','I review daily.','NOUN',999,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """, lesson);
    String save = "learning/notebook/from-language-item/" + source;
    var created = post(browser, json, base, save, java.util.Map.of(), true);
    assertEquals(200, created.statusCode(), created.body());
    var card = json.readTree(created.body());
    String id = card.get("id").asString();
    assertEquals(card, json.readTree(post(browser, json, base, save, java.util.Map.of(), true).body()));
    assertEquals("/rɪˈvjuː/", card.get("ipa").asString());
    String legacy = "learning/cards/" + id + "/attempts";
    String notebook = "learning/notebook/" + id + "/attempts";
    var mark = java.util.Map.of("result", "REMEMBERED", "clientRequestId", "cross-api-0001");
    assertEquals(200, post(browser, json, base, legacy, mark, true).statusCode());
    var replay = post(browser, json, base, notebook, java.util.Map.of("mode", "FLASHCARD", "result", "REMEMBERED", "clientRequestId", "cross-api-0001"), true);
    assertEquals(200, replay.statusCode(), replay.body());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM review_attempts WHERE review_item_id=?", Integer.class, id));
    assertEquals(409, post(browser, json, base, legacy, java.util.Map.of("result", "NEEDS_REVIEW", "clientRequestId", "cross-api-0001"), true).statusCode());
    assertEquals(404, post(outsider, json, base, legacy, mark, true).statusCode());
    assertEquals(404, request(outsider, json, base, "learning/notebook/" + id, "GET", null, false).statusCode());

    var reverse = java.util.Map.of("mode", "FLASHCARD", "result", "NEEDS_REVIEW", "clientRequestId", "cross-api-0002");
    assertEquals(200, post(browser, json, base, notebook, reverse, true).statusCode());
    assertEquals(200, post(browser, json, base, legacy, java.util.Map.of("result", "NEEDS_REVIEW", "clientRequestId", "cross-api-0002"), true).statusCode());
    assertEquals(0L, jdbc.queryForObject("SELECT schedule_version FROM review_items WHERE id=?", Long.class, id));
    assertNull(jdbc.queryForObject("SELECT next_review_at FROM review_items WHERE id=?", java.sql.Timestamp.class, id));

    jdbc.update("""
        INSERT INTO review_attempts(review_item_id,review_mode,result,client_request_id,is_correct,reviewed_at)
        VALUES (?,'FLASHCARD','REMEMBERED','legacy-no-fingerprint',NULL,UTC_TIMESTAMP(6)),
               (?,'QUICK_QUIZ','REMEMBERED','legacy-quiz-key',TRUE,UTC_TIMESTAMP(6))
        """, id, id);
    var oldMark = java.util.Map.of("result", "REMEMBERED", "clientRequestId", "legacy-no-fingerprint");
    assertEquals(200, post(browser, json, base, legacy, oldMark, true).statusCode());
    assertEquals(200, post(browser, json, base, notebook, java.util.Map.of("mode", "FLASHCARD", "result", "REMEMBERED", "clientRequestId", "legacy-no-fingerprint"), true).statusCode());
    assertEquals(409, post(browser, json, base, legacy, java.util.Map.of("result", "NEEDS_REVIEW", "clientRequestId", "legacy-no-fingerprint"), true).statusCode());
    assertEquals(409, post(browser, json, base, legacy, java.util.Map.of("result", "REMEMBERED", "clientRequestId", "legacy-quiz-key"), true).statusCode());
    assertEquals(400, post(browser, json, base, notebook, java.util.Map.of("mode", "QUICK_QUIZ", "optionId", "old", "clientRequestId", "legacy-quiz-key"), true).statusCode());
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM review_attempts WHERE review_item_id=? AND request_fingerprint IS NULL", Integer.class, id));
    assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT is_correct FROM review_attempts WHERE review_item_id=? AND review_mode='QUICK_QUIZ'", Boolean.class, id));

    var scheduled = java.util.Map.of("mode", "SPACED_REVIEW", "rating", "GOOD", "scheduleVersion", 0, "clientRequestId", "cross-spaced-0001");
    var graded = post(browser, json, base, notebook, scheduled, true);
    assertEquals(200, graded.statusCode(), graded.body());
    assertEquals(409, post(browser, json, base, legacy, java.util.Map.of("result", "NEEDS_REVIEW", "clientRequestId", "cross-spaced-0001"), true).statusCode());
    assertEquals(json.readTree(graded.body()), json.readTree(post(browser, json, base, notebook, scheduled, true).body()));
    var next = java.util.Map.of("mode", "SPACED_REVIEW", "rating", "GOOD", "scheduleVersion", 1, "clientRequestId", "cross-spaced-0002");
    assertEquals(409, post(browser, json, base, notebook, next, true).statusCode());
    jdbc.update("UPDATE review_items SET next_review_at='2026-11-01' WHERE id=?", id);
    assertEquals(200, post(browser, json, base, notebook, next, true).statusCode());

    jdbc.update("DELETE FROM language_items WHERE id=?", source);
    var detached = json.readTree(request(browser, json, base, "learning/notebook/" + id, "GET", null, false).body());
    assertTrue(detached.get("sourceLanguageItemId").isNull());
    for (String field : List.of("expression", "meaningVi", "exampleEn", "ipa", "partOfSpeech"))
      assertEquals(card.get(field), detached.get(field));
    assertEquals(204, request(browser, json, base, "learning/notebook/" + id, "DELETE", null, true).statusCode());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM review_attempts WHERE review_item_id=?", Integer.class, id));
  }

  @Test
  void spacedReviewsOwnTheQueueAndPersistOneSchedulePerRating() throws Exception {
    var browser = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var outsider = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var json = new tools.jackson.databind.ObjectMapper();
    var base = "http://127.0.0.1:" + port + "/api/v1/";
    var password = "Synthetic spaced review password";
    var account = accounts.register("spaced-" + java.util.UUID.randomUUID() + "@example.test", password);
    var different = accounts.register("other-" + java.util.UUID.randomUUID() + "@example.test", password);
    assertEquals(200, post(browser, json, base, "auth/login",
        java.util.Map.of("email", account.email(), "password", password), true).statusCode());
    assertEquals(200, post(outsider, json, base, "auth/login",
        java.util.Map.of("email", different.email(), "password", password), true).statusCode());

    long owner = Long.parseLong(account.id());
    jdbc.update("""
        INSERT INTO review_items(user_id,item_type,expression,meaning_vi,example_en,status,created_at,updated_at)
        SELECT ?,'VOCABULARY',expression,meaning_vi,example_en,'REMEMBERED','2026-10-01','2026-10-01'
        FROM language_items ORDER BY id LIMIT 11
        """, owner);
    var cardIds = jdbc.queryForList("SELECT id FROM review_items WHERE user_id=? ORDER BY id", Long.class, owner);
    String queue = "learning/notebook/spaced";
    var firstPage = json.readTree(request(browser, json, base, queue, "GET", null, false).body());
    assertEquals(11, firstPage.get("newCount").asInt());
    assertEquals(10, firstPage.get("newEligibleCount").asInt());
    assertEquals("2026-11-28T00:00:00+07:00",
        java.time.Instant.parse(firstPage.get("nextLocalDayAt").asString())
            .atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime()
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    var items = firstPage.get("items");
    assertEquals(10, items.size());
    for (int i = 0; i < items.size(); i++)
      assertEquals(cardIds.get(i).toString(), items.get(i).get("card").get("id").asString());
    assertEquals(0, json.readTree(request(outsider, json, base, queue, "GET", null, false).body()).get("newCount").asInt());

    String attempts = "learning/notebook/" + cardIds.getFirst() + "/attempts";
    var grade = java.util.Map.of("mode", "SPACED_REVIEW", "rating", "GOOD", "scheduleVersion", 0, "clientRequestId", "first");
    assertEquals(404, post(outsider, json, base, attempts, grade, true).statusCode());
    assertEquals(403, post(browser, json, base, attempts, grade, false).statusCode());
    var otherTab = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    assertEquals(200, post(otherTab, json, base, "auth/login",
        java.util.Map.of("email", account.email(), "password", password), true).statusCode());
    var alternate = java.util.Map.of("mode", "SPACED_REVIEW", "rating", "GOOD", "scheduleVersion", 0, "clientRequestId", "another");
    var one = CompletableFuture.supplyAsync(() -> {
      try { return post(browser, json, base, attempts, grade, true); }
      catch (Exception failure) { throw new CompletionException(failure); }
    });
    var two = CompletableFuture.supplyAsync(() -> {
      try { return post(otherTab, json, base, attempts, alternate, true); }
      catch (Exception failure) { throw new CompletionException(failure); }
    });
    var left = one.join();
    var right = two.join();
    assertEquals(List.of(200, 409), java.util.stream.Stream.of(left, right).map(r -> r.statusCode()).sorted().toList());
    var saved = json.readTree((left.statusCode() == 200 ? left : right).body());
    assertTrue((left.statusCode() == 409 ? left : right).body().contains("REVIEW_SCHEDULE_CHANGED"));
    assertEquals("2026-11-30T02:00:00Z", saved.get("nextReviewAt").asString());
    assertEquals("REMEMBERED", saved.get("result").asString());
    String acceptedKey = left.statusCode() == 200 ? "first" : "another";
    assertEquals(saved, json.readTree(post(browser, json, base, attempts,
        left.statusCode() == 200 ? grade : alternate, true).body()));
    var changed = post(browser, json, base, attempts, java.util.Map.of(
        "mode", "SPACED_REVIEW", "rating", "EASY", "scheduleVersion", 0, "clientRequestId", acceptedKey), true);
    assertEquals(409, changed.statusCode());
    assertTrue(changed.body().contains("REQUEST_ID_REUSED"));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM review_attempts WHERE review_item_id=?", Integer.class, cardIds.getFirst()));
    var early = post(browser, json, base, attempts, java.util.Map.of(
        "mode", "SPACED_REVIEW", "rating", "HARD", "scheduleVersion", 1, "clientRequestId", "early"), true);
    assertEquals(409, early.statusCode());
    assertTrue(early.body().contains("REVIEW_NOT_DUE"));

    assertEquals(200, post(browser, json, base, attempts, java.util.Map.of(
        "mode", "FLASHCARD", "result", "NEEDS_REVIEW", "clientRequestId", "manual"), true).statusCode());
    assertEquals(1L, jdbc.queryForObject("SELECT schedule_version FROM review_items WHERE id=?", Long.class, cardIds.getFirst()));
    for (int i = 1; i < 10; i++) {
      assertEquals(200, post(browser, json, base, "learning/notebook/" + cardIds.get(i) + "/attempts", grade, true).statusCode());
    }
    var capped = post(browser, json, base, "learning/notebook/" + cardIds.getLast() + "/attempts", grade, true);
    assertEquals(409, capped.statusCode());
    assertTrue(capped.body().contains("NEW_REVIEW_LIMIT_REACHED"));

    jdbc.update("UPDATE review_items SET next_review_at='2026-11-27 02:00:00' WHERE id=?", cardIds.getFirst());
    var due = json.readTree(request(browser, json, base, queue, "GET", null, false).body());
    assertEquals(1, due.get("dueCount").asInt());
    assertEquals(0, due.get("newRemainingToday").asInt());
    var relearn = post(browser, json, base, attempts, java.util.Map.of(
        "mode", "SPACED_REVIEW", "rating", "AGAIN", "scheduleVersion", 1, "clientRequestId", "relearn"), true);
    assertEquals(200, relearn.statusCode(), relearn.body());
    assertEquals("2026-11-27T02:10:00Z", json.readTree(relearn.body()).get("nextReviewAt").asString());
    assertEquals(saved, json.readTree(post(browser, json, base, attempts,
        left.statusCode() == 200 ? grade : alternate, true).body()));

    jdbc.update("UPDATE review_items SET first_spaced_reviewed_at='2026-11-26 17:00:00' WHERE id=?", cardIds.get(1));
    assertEquals(0, json.readTree(request(browser, json, base, queue, "GET", null, false).body())
        .get("newRemainingToday").asInt());
    jdbc.update("UPDATE review_items SET first_spaced_reviewed_at='2026-11-26 16:59:59' WHERE id=?", cardIds.get(1));
    assertEquals(1, json.readTree(request(browser, json, base, queue, "GET", null, false).body())
        .get("newRemainingToday").asInt());
    jdbc.update("""
        INSERT INTO practice_schedules(user_id, local_time, time_zone, created_at, updated_at)
        VALUES (?,'08:00:00','America/Los_Angeles',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        ON DUPLICATE KEY UPDATE time_zone='America/Los_Angeles'
        """, owner);
    var shifted = json.readTree(request(browser, json, base, queue, "GET", null, false).body());
    assertEquals("America/Los_Angeles", shifted.get("timeZone").asString());
    assertEquals("2026-11-27T08:00:00Z", shifted.get("nextLocalDayAt").asString());
    assertEquals("2026-11-27T02:10:00.000000Z", shifted.get("nextDueAt").asString());
    assertEquals(204, request(browser, json, base, "learning/data", "DELETE", null, true).statusCode());
    assertEquals(404, post(browser, json, base, attempts, grade, true).statusCode());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM review_attempts WHERE review_item_id=?", Integer.class, cardIds.getFirst()));
  }

  @Test
  void learningCardsSurviveSourceDeletionAndWholeDeleteIsOwnerScoped() {
    var owner = owner();
    var other = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    long otherId = Long.parseLong(service.create(other, draft()).id());
    service.send(owner, id, new SendTextRequest("Soup, please.", "learn-card-turn"));
    service.finish(owner, id);
    provider.feedbackOutput = CLEAN_REVIEW;
    long suggestion =
        Long.parseLong(
            service.generateFeedback(owner, id, false, false).suggestionIds().getFirst());
    assertEquals(
        404, assertThrows(ApiException.class, () -> learning.save(other, suggestion)).status);
    var saved = learning.save(owner, suggestion);
    assertEquals(saved, learning.save(owner, suggestion));
    long card = Long.parseLong(saved.id());
    var answer =
        new vn.englishconversation.learning.dto.LearningData.ReviewAnswer(
            "REMEMBERED", "review-attempt-1");
    assertEquals(
        404, assertThrows(ApiException.class, () -> learning.answer(other, card, answer)).status);
    assertEquals("REMEMBERED", learning.answer(owner, card, answer).status());
    assertEquals("REMEMBERED", learning.answer(owner, card, answer).status());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM review_attempts WHERE review_item_id=?", Integer.class, card));
    assertEquals(
        409,
        assertThrows(
                ApiException.class,
                () ->
                    learning.answer(
                        owner,
                        card,
                        new vn.englishconversation.learning.dto.LearningData.ReviewAnswer(
                            "NEEDS_REVIEW", "review-attempt-1")))
            .status);
    assertEquals(1, learning.history(owner, 0, 1).total());
    assertEquals(Long.toString(id), learning.history(owner, 0, 1).items().getFirst().id());
    assertTrue(learning.history(owner, 1, 1).items().isEmpty());
    assertEquals(Long.toString(otherId), learning.history(other, 0, 1).items().getFirst().id());
    assertEquals(
        404, assertThrows(ApiException.class, () -> learning.deleteSession(other, id)).status);
    learning.deleteSession(owner, id);
    assertEquals(404, assertThrows(ApiException.class, () -> service.get(owner, id)).status);
    assertEquals(
        404, assertThrows(ApiException.class, () -> service.getFeedback(owner, id)).status);
    assertEquals(
        404, assertThrows(ApiException.class, () -> learning.save(owner, suggestion)).status);
    var retained = learning.cards(owner, null, 0, 12).items().getFirst();
    assertEquals(saved.expression(), retained.expression());
    assertEquals("REMEMBERED", retained.status());
    assertNull(retained.sourceSuggestionId());
    assertEquals(0, learning.history(owner, 0, 12).total());
    assertEquals(0, learning.statistics(owner).learnerTurns());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ai_requests WHERE user_id=? AND session_id IS NOT NULL",
            Integer.class,
            owner.id()));
    assertTrue(
        jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_requests WHERE user_id=?", Integer.class, owner.id())
            > 0);
    learning.deleteAll(owner);
    learning.deleteAll(owner);
    assertEquals(0, learning.cards(owner, null, 0, 12).total());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM review_attempts WHERE review_item_id=?", Integer.class, card));
    assertEquals(Long.toString(otherId), service.get(other, otherId).id());
  }
}
