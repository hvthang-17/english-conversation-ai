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
class PracticeMysqlTest extends FullSystemMysqlFixture {

  @Test
  void clubIntroOffersBothRolesAndStartsInTheSelectedRole() throws Exception {
    var browser = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var json = new tools.jackson.databind.ObjectMapper();
    var base = "http://127.0.0.1:" + port + "/api/v1/";
    var password = "Synthetic club role password";
    var account = accounts.register("club-roles-" + java.util.UUID.randomUUID() + "@example.test", password);
    assertEquals(200, post(browser, json, base, "auth/login",
        java.util.Map.of("email", account.email(), "password", password), true).statusCode());
    String scenario = jdbc.queryForObject(
        "SELECT CAST(id AS CHAR) FROM scenarios WHERE code='CLUB_INTRO'", String.class);
    var catalog = request(browser, json, base, "scenarios/" + scenario + "?level=A2", "GET", null, false);
    assertEquals(200, catalog.statusCode());
    var roles = json.readTree(catalog.body()).get("roles");
    assertEquals(2, roles.size());
    assertEquals("Thành viên mới", roles.get(0).get("learnerRole").asString());
    assertEquals("Thành viên đón tiếp", roles.get(1).get("learnerRole").asString());
    assertEquals("Thành viên mới", roles.get(1).get("aiRole").asString());
    assertFalse(catalog.body().contains("Play a new club member"));

    var created = post(browser, json, base, "practice-sessions",
        new CreatePracticeRequest(scenario, "A2", "club-intro-reverse", "TEXT", "REVERSE"), true);
    assertEquals(200, created.statusCode(), created.body());
    var practice = json.readTree(created.body());
    assertEquals("REVERSE", practice.get("roleCode").asString());
    assertEquals("Thành viên đón tiếp", practice.get("learnerRole").asString());
    assertEquals("Thành viên mới", practice.get("aiRole").asString());
    assertTrue(provider.lastPrompt.instruction().contains("Play a new club member"));
  }

  @Test
  void selectingRoleChangesPromptAndPreservesSnapshotAndRequestIdentity() {
    var owner = owner();
    String scenario = jdbc.queryForObject(
        "SELECT CAST(id AS CHAR) FROM scenarios WHERE code='SHOP_EXCHANGE'", String.class);
    String key = "exchange-reverse";
    var reverse = new CreatePracticeRequest(scenario, "B1", key, "VOICE", "REVERSE");
    var created = service.create(owner, reverse);
    assertEquals("REVERSE", created.roleCode());
    assertEquals("Hi, I'd like to exchange this shirt.", created.messages().getFirst().content());
    assertEquals("Nhân viên quầy đổi hàng", created.learnerRole());
    assertEquals("Khách hàng", created.aiRole());
    assertTrue(created.communicationGoal().contains("hai phương án"));
    assertTrue(provider.lastPrompt.instruction().contains("Play a customer"));
    assertTrue(provider.lastPrompt.instruction().contains("Open naturally in the partner role"));
    assertFalse(provider.lastPrompt.instruction().contains("one relevant question"));
    assertEquals(created.id(), service.create(owner, reverse).id());
    assertEquals(409, assertThrows(ApiException.class,
        () -> service.create(owner, new CreatePracticeRequest(scenario, "B1", key, "VOICE"))).status);
    assertEquals(404, assertThrows(ApiException.class, () -> service.create(owner,
        new CreatePracticeRequest(scenario, "B1", "unknown-role", "TEXT", "MISSING"))).status);

    assertEquals(404, assertThrows(ApiException.class, () -> service.create(owner,
        new CreatePracticeRequest(scenario, "A2", "exchange-wrong-level", "TEXT"))).status);

    var standard = service.create(owner, new CreatePracticeRequest(scenario, "B1", "exchange-default"));
    assertEquals("DEFAULT", standard.roleCode());
    assertEquals("Khách hàng", standard.learnerRole());
    assertEquals("Nhân viên quầy đổi hàng", standard.aiRole());
    assertEquals("TEXT", standard.mode());
    assertTrue(provider.lastPrompt.instruction().contains("Play the returns clerk"));
    long id = Long.parseLong(created.id());
    String roleSql = "UPDATE scenario_role_variants SET learner_role=? WHERE scenario_id=? AND code='REVERSE'";
    jdbc.update(roleSql, "Changed fixture role", Long.parseLong(scenario));
    try {
      var reopened = service.get(owner, id);
      assertEquals("B1", reopened.selectedLevel());
      assertEquals("VOICE", reopened.mode());
      assertEquals("REVERSE", reopened.roleCode());
      assertEquals(created.messages(), reopened.messages());
      assertTrue(learning.history(owner, 0, 10).items().stream().anyMatch(entry -> entry.id().equals(created.id()) && entry.title().equals(created.title())));
      assertEquals(created.learnerRole(), reopened.learnerRole());
      assertEquals(created.communicationGoal(), reopened.communicationGoal());
      service.send(owner, id, new SendTextRequest("May I see your receipt?", "exchange-role-turn"));
      assertTrue(provider.lastPrompt.instruction().contains("Learner role: Nhân viên quầy đổi hàng"));
      assertFalse(provider.lastPrompt.instruction().contains("Changed fixture role"));
      assertEquals("COMPLETED", service.finish(owner, id).status());
      provider.feedbackOutput = CLEAN_REVIEW;
      try {
        assertEquals("SUCCEEDED", service.generateFeedback(owner, id, false, false).status());
      } finally {
        provider.feedbackOutput = null;
      }
    } finally {
      jdbc.update(roleSql, created.learnerRole(), Long.parseLong(scenario));
    }
  }

  @Test
  void finishCountsOnlyPracticesWithSuccessfulExchanges() {
    var owner = owner();
    long openingOnly = Long.parseLong(service.create(owner, draft()).id());
    long failedOnly = Long.parseLong(service.create(owner, draft()).id());
    long successful = Long.parseLong(service.create(owner, draft()).id());
    long failedLast = Long.parseLong(service.create(owner, draft()).id());

    assertEquals("ABANDONED", service.finish(owner, openingOnly).status());
    provider.timeout = true;
    service.send(owner, failedOnly, new SendTextRequest("Soup please.", "finish-first-failed"));
    assertEquals("ABANDONED", service.finish(owner, failedOnly).status());
    assertEquals("Soup please.", service.get(owner, failedOnly).messages().getLast().content());
    assertEquals(409, assertThrows(ApiException.class,
        () -> service.generateFeedback(owner, failedOnly, false, false)).status);
    assertEquals(0, learning.statistics(owner).completedSessions());
    assertEquals(1, learning.statistics(owner).learnerTurns());

    service.send(owner, successful, new SendTextRequest("Tea please.", "finish-successful"));
    assertEquals("COMPLETED", service.finish(owner, successful).status());
    service.send(owner, failedLast, new SendTextRequest("Water please.", "finish-first-success"));
    provider.timeout = true;
    service.send(owner, failedLast, new SendTextRequest("No ice please.", "finish-last-failed"));
    var result = service.finish(owner, failedLast);
    assertEquals("COMPLETED", result.status());
    assertEquals("No ice please.", result.messages().getLast().content());
    assertEquals(result, service.finish(owner, failedLast));
    assertEquals(2, learning.statistics(owner).completedSessions());
    var statuses = learning.history(owner, 0, 12).items().stream()
        .collect(java.util.stream.Collectors.toMap(entry -> entry.id(), entry -> entry.status()));
    assertEquals("ABANDONED", statuses.get(Long.toString(failedOnly)));
    assertEquals("COMPLETED", statuses.get(Long.toString(failedLast)));
    provider.feedbackOutput = CLEAN_REVIEW;
    assertEquals("SUCCEEDED", service.generateFeedback(owner, failedLast, false, false).status());
  }

  @Test
  void createsSnapshotReplaysAndEnforcesOwnershipHintAndTurnLimit() {
    var owner = owner();
    var input = draft();
    var opening = service.create(owner, input);
    long id = Long.parseLong(opening.id());
    int before = provider.calls.get();
    assertEquals(opening.id(), service.create(owner, input).id());
    assertEquals(before, provider.calls.get());
    assertEquals(1, opening.messages().size());
    String catalogTitle =
        jdbc.queryForObject(
            "SELECT title FROM scenarios WHERE id=?", String.class, input.scenarioId());
    try {
      jdbc.update(
          "UPDATE scenarios SET title='Updated fixture title' WHERE id=?", input.scenarioId());
      assertEquals(opening.title(), service.get(owner, id).title());
    } finally {
      jdbc.update("UPDATE scenarios SET title=? WHERE id=?", catalogTitle, input.scenarioId());
    }
    assertEquals(404, assertThrows(ApiException.class, () -> service.get(owner(), id)).status);
    assertEquals(
        404,
        assertThrows(
                ApiException.class,
                () -> service.send(owner(), id, new SendTextRequest("Hello", "cross-owner")))
            .status);
    var hintRequest = new HintRequest(opening.messages().getFirst().id(), "hint-initial");
    var hint = service.hint(owner, id, hintRequest);
    assertNotNull(hint.hint());
    assertEquals(1, service.get(owner, id).messages().size());
    assertEquals(hint, service.hint(owner, id, hintRequest));
    for (int n = 1; n <= 20; n++) {
      var request = new SendTextRequest("I would like soup.", "learner-" + n);
      var response = service.send(owner, id, request);
      assertEquals(n, response.learnerTurns());
      assertEquals(n * 2 + 1, response.messages().size());
      assertEquals(n * 2 + 1, service.send(owner, id, request).messages().size());
    }
    assertEquals(
        "REQUEST_ID_REUSED",
        assertThrows(
                ApiException.class,
                () ->
                    service.send(
                        owner, id, new SendTextRequest("A different sentence.", "learner-20")))
            .code);
    assertEquals(
        "TURN_LIMIT",
        assertThrows(
                ApiException.class,
                () ->
                    service.send(
                        owner, id, new SendTextRequest("May I order again?", "learner-21")))
            .code);
    assertEquals("COMPLETED", service.finish(owner, id).status());
    assertEquals(41, service.get(owner, id).messages().size());
    assertEquals(
        22,
        jdbc.queryForObject(
            """
SELECT COUNT(*) FROM ai_attempts a JOIN ai_requests r ON r.id=a.ai_request_id
WHERE r.session_id=? AND a.latency_ms IS NOT NULL AND a.input_tokens=21 AND a.estimated_cost IS NULL
""",
            Integer.class,
            id));
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "G62_CONTEXT_FIXTURE", matches = "true")
  void trimsOldContextAndKeepsLatestMessageAndStoredTranscript() {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    String earliest = "Earlier context. " + "a".repeat(700);
    service.send(owner, id, new SendTextRequest(earliest, "context-first"));
    for (int n = 0; n < 3; n++)
      service.send(owner, id, new SendTextRequest("Recent context. " + "b".repeat(700), "context-middle-" + n));
    var response = service.send(owner, id, new SendTextRequest("Latest input.", "context-latest"));
    var prompt = provider.lastPrompt;
    assertEquals("Latest input.", prompt.history().getLast().content());
    assertTrue(prompt.history().stream().noneMatch(line -> earliest.equals(line.content())));
    assertTrue(prompt.instruction().length() + prompt.history().stream()
        .mapToInt(line -> line.content().length()).sum() <= 2400);
    assertEquals(5, response.learnerTurns());
    assertTrue(response.messages().stream().anyMatch(line -> earliest.equals(line.content())));
  }

  @Test
  void timeoutKeepsLearnerTextAndRetryReusesTurn() {
    var owner = owner();
    var opening = service.create(owner, draft());
    long id = Long.parseLong(opening.id());
    provider.timeout = true;
    var text = new SendTextRequest("Soup, please.", "timeout-turn");
    var failed = service.send(owner, id, text);
    assertEquals("UNKNOWN", failed.operation().status());
    assertEquals(
        List.of("ASSISTANT", "LEARNER"),
        failed.messages().stream().map(MessageResponse::sender).toList());
    long operation = Long.parseLong(failed.operation().id());
    assertEquals(
        "UNKNOWN_ACK_REQUIRED",
        assertThrows(
                ApiException.class,
                () -> service.retry(owner, id, operation, new RetryRequest(false)))
            .code);
    var completed = service.retry(owner, id, operation, new RetryRequest(true));
    assertEquals(1, completed.learnerTurns());
    assertEquals(3, completed.messages().size());
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ai_attempts WHERE ai_request_id=?", Integer.class, operation));
    assertEquals(3, service.send(owner, id, text).messages().size());
  }

  @Test
  void concurrentCallsSerializeSessionAndEnforceGlobalQuotaBeforeDispatch() throws Exception {
    var owner = owner();
    var opening = service.create(owner, draft());
    long id = Long.parseLong(opening.id());
    long second = Long.parseLong(service.create(owner, draft()).id());
    provider.entered = new CountDownLatch(1);
    provider.release = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var request = new SendTextRequest("Hello again.", "blocked-turn");
      var running = executor.submit(() -> service.send(owner, id, request));
      try {
        assertTrue(provider.entered.await(5, TimeUnit.SECONDS));
        assertEquals("RUNNING", service.send(owner, id, request).operation().status());
        assertEquals(
            "PRACTICE_BUSY",
            assertThrows(
                    ApiException.class,
                    () ->
                        service.send(
                            owner, id, new SendTextRequest("Another input.", "other-turn")))
                .code);
        assertEquals(
            "PRACTICE_BUSY",
            assertThrows(
                    ApiException.class,
                    () -> service.hint(owner, id, new HintRequest(opening.messages().getFirst().id(), "blocked-hint")))
                .code);
        assertEquals(
            "AI_QUOTA",
            assertThrows(
                    ApiException.class,
                    () ->
                        service.send(
                            owner, second, new SendTextRequest("Other session.", "quota-turn")))
                .code);
        assertEquals(0, service.get(owner, second).learnerTurns());
      } finally {
        provider.release.countDown();
      }
      assertEquals(3, running.get(6, TimeUnit.SECONDS).messages().size());
    } finally {
      provider.entered = null;
      provider.release = null;
    }
  }
}
