package vn.englishconversation.conversation;


import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.conversation.dto.*;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class PracticeHttpMysqlTest extends FullSystemMysqlFixture {

  @Test
  void httpFlowEnforcesSessionAndCsrf() throws Exception {
    var browser =
        java.net.http.HttpClient.newBuilder()
            .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL))
            .build();
    var json = new tools.jackson.databind.ObjectMapper();
    String base = "http://127.0.0.1:" + port + "/api/v1/";
    var anonymous =
        browser.send(
            java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "practice-sessions/1"))
                .GET()
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(401, anonymous.statusCode());
    var credentials =
        java.util.Map.of(
            "email",
            "http-chat-" + java.util.UUID.randomUUID() + "@example.test",
            "password",
            "Synthetic HTTP fixture password");
    assertEquals(201, post(browser, json, base, "auth/register", credentials, true).statusCode());
    assertEquals(200, post(browser, json, base, "auth/login", credentials, true).statusCode());
    assertEquals(403, post(browser, json, base, "practice-sessions", draft(), false).statusCode());
    var input = draft();
    var voiceInput = new CreatePracticeRequest(input.scenarioId(), input.level(), input.clientRequestId(), "VOICE");
    var invalidInput = new CreatePracticeRequest(input.scenarioId(), input.level(), input.clientRequestId(), "invalid");
    assertEquals(400, post(browser, json, base, "practice-sessions", invalidInput, true).statusCode());
    var created = post(browser, json, base, "practice-sessions", voiceInput, true);
    assertEquals(200, created.statusCode(), created.body());
    var opening = json.readTree(created.body());
    assertEquals("VOICE", opening.get("mode").asString());
    assertFalse(opening.has("instruction"));
    String path = "practice-sessions/" + opening.get("id").asString();
    String explanationPath = path + "/messages/" + opening.get("messages").get(0).get("id").asString()
        + "/explanation";
    var explanationInput = new ExplanationRequest("http-explanation", false, false);
    assertEquals(403, post(browser, json, base, explanationPath, explanationInput, false).statusCode());
    var explanation = post(browser, json, base, explanationPath, explanationInput, true);
    assertEquals(200, explanation.statusCode(), explanation.body());
    assertEquals("SUCCEEDED", json.readTree(explanation.body()).get("status").asString());
    assertEquals(json.readTree(explanation.body()), json.readTree(
        request(browser, json, base, explanationPath, "GET", null, false).body()));
    assertEquals(403, multipart(browser, json, base, path, false, 160).statusCode());
    int callsBeforeUpload = provider.calls.get();
    assertEquals(413, multipart(browser, json, base, path, true, 480001).statusCode());
    assertEquals(callsBeforeUpload, provider.calls.get());
    var voice = multipart(browser, json, base, path, true, 480000);
    assertEquals(200, voice.statusCode(), voice.body());
    var voiceBody = json.readTree(voice.body());
    assertEquals("READY", voiceBody.get("status").asString());
    assertEquals(30000, voiceBody.get("durationMillis").asInt());
    assertFalse(voiceBody.has("storageKey"));
    var recovered = request(browser, json, base, path + "/audio", "GET", null, false);
    assertEquals(200, recovered.statusCode());
    assertEquals(voiceBody, json.readTree(recovered.body()));
    assertEquals(401, request(java.net.http.HttpClient.newHttpClient(), json, base,
        path + "/audio", "GET", null, false).statusCode());
    String voicePath = path + "/audio/" + voiceBody.get("id").asString();
    assertEquals(403, post(browser, json, base, voicePath + "/confirm",
        java.util.Map.of("content", "I would like soup."), false).statusCode());
    var sent =
        post(
            browser,
            json,
            base,
            path + "/turns",
            new SendTextRequest("Soup, please.", "http-turn-1"),
            true);
    assertEquals(200, sent.statusCode());
    assertEquals(3, json.readTree(sent.body()).get("messages").size());
    String sourceId = json.readTree(sent.body()).get("messages").get(2).get("id").asString();
    var hint = post(browser, json, base, path + "/hints", new HintRequest(sourceId, "http-hint-1"), true);
    assertEquals(200, hint.statusCode());
    assertTrue(json.readTree(hint.body()).get("hint").isString());
    var recoveredHint = request(browser, json, base, path + "/messages/" + sourceId + "/hint", "GET", null, false);
    assertEquals(200, recoveredHint.statusCode());
    assertEquals(json.readTree(hint.body()), json.readTree(recoveredHint.body()));
    var reopened = request(browser, json, base, path, "GET", null, false);
    assertEquals(3, json.readTree(reopened.body()).get("messages").size());
    assertEquals(json.readTree(hint.body()), json.readTree(reopened.body()).get("hint"));
    long sessionId = Long.parseLong(opening.get("id").asString());
    var switchRequest = java.util.Map.of("mode", "TEXT");
    assertEquals("VOICE", json.readTree(
        request(browser, json, base, path, "GET", null, false).body()).get("mode").asString());
    assertEquals(403, request(browser, json, base, path + "/mode", "PATCH", switchRequest, false).statusCode());
    assertEquals(404, assertThrows(ApiException.class, () -> service.switchToText(owner(), sessionId)).status);
    assertEquals(400, request(browser, json, base, path + "/mode", "PATCH",
        java.util.Map.of("mode", "VOICE"), true).statusCode());
    var switched = request(browser, json, base, path + "/mode", "PATCH", switchRequest, true);
    assertEquals(200, switched.statusCode(), switched.body());
    var afterSwitch = json.readTree(switched.body());
    assertEquals("TEXT", afterSwitch.get("mode").asString());
    assertEquals(1, afterSwitch.get("learnerTurns").asInt());
    assertEquals(json.readTree(sent.body()).get("messages"), afterSwitch.get("messages"));
    assertEquals(afterSwitch, json.readTree(request(browser, json, base,
        path + "/mode", "PATCH", switchRequest, true).body()));
    int beforeReplay = provider.calls.get();
    var replay = post(browser, json, base, "practice-sessions", voiceInput, true);
    assertEquals(200, replay.statusCode(), replay.body());
    assertEquals(afterSwitch.get("id"), json.readTree(replay.body()).get("id"));
    assertEquals("TEXT", json.readTree(replay.body()).get("mode").asString());
    assertEquals(409, post(browser, json, base, "practice-sessions", input, true).statusCode());
    assertEquals(beforeReplay, provider.calls.get());
    var finished = post(browser, json, base, path + "/finish", java.util.Map.of(), true);
    assertEquals("COMPLETED", json.readTree(finished.body()).get("status").asString());
    assertEquals(204, request(browser, json, base, path + "/audio", "GET", null, false).statusCode());
    assertEquals(
        403, post(browser, json, base, path + "/feedback", java.util.Map.of(), false).statusCode());
    provider.feedbackOutput = CLEAN_REVIEW;
    var review = post(browser, json, base, path + "/feedback", java.util.Map.of(), true);
    assertEquals(200, review.statusCode(), review.body());
    assertEquals("SUCCEEDED", json.readTree(review.body()).get("status").asString());
    var reloaded =
        browser.send(
            java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + path + "/feedback"))
                .GET()
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(200, reloaded.statusCode());
    assertEquals(json.readTree(review.body()), json.readTree(reloaded.body()));
    String suggestion = json.readTree(review.body()).get("suggestionIds").get(0).asString();
    var card =
        post(
            browser,
            json,
            base,
            "learning/cards/from-suggestion/" + suggestion,
            java.util.Map.of(),
            true);
    assertEquals(200, card.statusCode(), card.body());
    assertEquals(suggestion, json.readTree(card.body()).get("sourceSuggestionId").asString());
    assertEquals("NOUN", json.readTree(card.body()).get("partOfSpeech").asString());
    String savedCardId = json.readTree(card.body()).get("id").asString();
    assertEquals(json.readTree(card.body()), json.readTree(post(browser, json, base,
        "learning/cards/from-suggestion/" + suggestion, java.util.Map.of(), true).body()));
    jdbc.update("UPDATE feedback_suggestions SET source_json=JSON_SET(COALESCE(source_json,JSON_OBJECT()),'$.meaningVerified',CAST('false' AS JSON)) WHERE id=?", suggestion);
    assertEquals(409, post(browser, json, base, "learning/cards/from-suggestion/" + suggestion,
        java.util.Map.of(), true).statusCode());
    jdbc.update("UPDATE feedback_suggestions SET source_json=JSON_SET(source_json,'$.meaningVerified',CAST('true' AS JSON)) WHERE id=?", suggestion);
    assertEquals(200, post(browser, json, base, "learning/cards/" + savedCardId + "/attempts",
        java.util.Map.of("result", "REMEMBERED", "clientRequestId", "suggestion-copy-review"), true).statusCode());
    String history = base + "learning/history/" + opening.get("id").asString();
    var delete = java.net.http.HttpRequest.newBuilder(java.net.URI.create(history)).DELETE();
    assertEquals(
        403,
        browser
            .send(delete.build(), java.net.http.HttpResponse.BodyHandlers.discarding())
            .statusCode());
    var csrf =
        json.readTree(
            browser
                .send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "auth/csrf"))
                        .GET()
                        .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString())
                .body());
    delete.header(csrf.get("headerName").asString(), csrf.get("token").asString());
    assertEquals(
        204,
        browser
            .send(delete.build(), java.net.http.HttpResponse.BodyHandlers.discarding())
            .statusCode());
    var stats =
        browser.send(
            java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "learning/stats"))
                .GET()
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(200, stats.statusCode());
    assertEquals(0, json.readTree(stats.body()).get("learnerTurns").asInt());
    assertNull(jdbc.queryForObject("SELECT source_suggestion_id FROM review_items WHERE id=?", Long.class, savedCardId));
    var savedCopy = json.readTree(request(browser, json, base, "learning/notebook/" + savedCardId, "GET", null, false).body());
    for (String field : List.of("expression", "meaningVi", "exampleEn", "partOfSpeech"))
      assertEquals(json.readTree(card.body()).get(field), savedCopy.get(field));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM review_attempts WHERE review_item_id=?", Integer.class, savedCardId));
    assertEquals(204, request(browser, json, base, "learning/notebook/" + savedCardId, "DELETE", null, true).statusCode());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM review_attempts WHERE review_item_id=?", Integer.class, savedCardId));
  }
}
