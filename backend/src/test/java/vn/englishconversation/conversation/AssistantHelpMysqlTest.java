package vn.englishconversation.conversation;

import static vn.englishconversation.testing.FixtureProvider.VALID_EXPLANATION;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.conversation.dto.*;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class AssistantHelpMysqlTest extends FullSystemMysqlFixture {

  @Test
  void explainsLatestAssistantOnceAndKeepsItOutsideDialogue() throws Exception {
    var owner = owner();
    var practice = service.create(owner, voicePractice());
    long id = Long.parseLong(practice.id());
    long opening = Long.parseLong(practice.messages().getFirst().id());
    var input = new ExplanationRequest("explain-first", false, false);
    assertEquals("NOT_STARTED", service.getExplanation(owner, id, opening).status());
    assertEquals(404, assertThrows(ApiException.class,
        () -> service.explain(owner(), id, opening, input)).status);
    int before = provider.calls.get();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    provider.entered = entered;
    provider.release = release;
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var job = executor.submit(() -> service.explain(owner, id, opening, input));
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertEquals("RUNNING", service.explain(owner, id, opening, input).status());
        assertEquals(409, assertThrows(ApiException.class, () -> service.send(owner, id,
            new SendTextRequest("Soup, please.", "explain-overlap"))).status);
      } finally {
        release.countDown();
        provider.entered = null;
        provider.release = null;
      }
      var result = job.get(5, TimeUnit.SECONDS);
      assertEquals("SUCCEEDED", result.status());
      assertEquals("What do you want to order?", result.simpleEnglish());
      assertEquals("Bạn muốn gọi món gì?", result.meaningVi());
      assertEquals(result, service.getExplanation(owner, id, opening));
      assertEquals(result, service.explain(owner, id, opening, input));
      assertEquals(before + 1, provider.calls.get());
    }
    assertEquals(practice.messages(), service.get(owner, id).messages());
    assertEquals(0, service.get(owner, id).learnerTurns());
    var sent = service.send(owner, id, new SendTextRequest("Soup, please.", "explain-normal-turn"));
    assertFalse(provider.lastPrompt.history().stream().anyMatch(item ->
        item.content().equals("What do you want to order?")));
    long newest = Long.parseLong(sent.messages().getLast().id());
    assertEquals(409, assertThrows(ApiException.class, () -> service.explain(owner, id, newest, input)).status);
    var another = service.create(owner, voicePractice());
    long anotherId = Long.parseLong(another.id());
    long anotherOpening = Long.parseLong(another.messages().getFirst().id());
    assertEquals(404, assertThrows(ApiException.class,
        () -> service.explain(owner, id, anotherOpening, input)).status);
    service.send(owner, anotherId, new SendTextRequest("Soup, please.", "another-chat-turn"));
    assertEquals(409, assertThrows(ApiException.class, () -> service.explain(owner, anotherId, anotherOpening,
        new ExplanationRequest("obsolete-explain", false, false))).status);
    assertEquals(404, assertThrows(ApiException.class, () -> service.getExplanation(owner, id,
        Long.parseLong(sent.messages().get(1).id()))).status);
    learning.deleteSession(owner, id);
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM assistant_explanations WHERE session_id=?",
        Integer.class, id));
  }

  @Test
  void explanationHandlesTimeoutValidationRetryAndQuota() {
    var owner = owner();
    var practice = service.create(owner, draft());
    long id = Long.parseLong(practice.id());
    long opening = Long.parseLong(practice.messages().getFirst().id());
    provider.timeout = true;
    var failure = service.explain(owner, id, opening, new ExplanationRequest("explain-retry", false, false));
    assertEquals("UNKNOWN", failure.status());
    assertTrue(failure.retryable());
    assertEquals(409, assertThrows(ApiException.class, () -> service.explain(owner, id, opening,
        new ExplanationRequest("explain-retry", true, false))).status);
    provider.explanationOutput = """
        {"simpleEnglish":"What do you want to order?","meaningVi":" "} {}
        """;
    try {
      var invalid = service.explain(owner, id, opening, new ExplanationRequest("explain-retry", true, true));
      assertEquals("EXPLANATION_INVALID", invalid.errorCode());
      assertFalse(invalid.retryable());
      assertNull(invalid.simpleEnglish());
    } finally {
      provider.explanationOutput = VALID_EXPLANATION;
    }
    var next = service.send(owner, id, new SendTextRequest("Soup please.", "explain-after-timeout"));
    assertEquals(409, assertThrows(ApiException.class, () -> service.explain(owner, id, opening,
        new ExplanationRequest("explain-retry", true, true))).status);
    Long operation = jdbc.queryForObject("SELECT ai_request_id FROM assistant_explanations WHERE session_id=?",
        Long.class, id);
    for (int number = 3; number <= 62; number++) {
      jdbc.update("""
          INSERT INTO ai_attempts(ai_request_id,attempt_no,model_name,status,started_at,finished_at)
          VALUES (?,?,'quota-fixture','FAILED',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
          """, operation, number);
    }
    long latest = Long.parseLong(next.messages().getLast().id());
    int before = provider.calls.get();
    assertEquals(429, assertThrows(ApiException.class, () -> service.explain(owner, id, latest,
        new ExplanationRequest("explain-quota", false, false))).status);
    assertEquals(before, provider.calls.get());
    assertEquals("NOT_STARTED", service.getExplanation(owner, id, latest).status());
  }

  @Test
  void hintRestoresAfterLostResponseAndDeduplicatesAcrossTabs() throws Exception {
    var owner = owner();
    var opening = service.create(owner, draft());
    long session = Long.parseLong(opening.id());
    String source = opening.messages().getFirst().id();
    long message = Long.parseLong(source);
    var original = new HintRequest(source, "hint-first-tab");
    var duplicate = new HintRequest(source, "hint-second-tab");
    provider.entered = new CountDownLatch(1);
    provider.release = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var request = executor.submit(() -> service.hint(owner, session, original));
      try {
        assertTrue(provider.entered.await(5, TimeUnit.SECONDS));
        assertEquals("RUNNING", service.getHint(owner, session, message).status());
        assertEquals("RUNNING", service.hint(owner, session, duplicate).status());
        assertEquals(404, assertThrows(ApiException.class, () -> service.getHint(owner(), session, message)).status);
      } finally {
        provider.release.countDown();
      }
      request.get(5, TimeUnit.SECONDS);
    } finally {
      provider.entered = null;
      provider.release = null;
    }
    var saved = service.getHint(owner, session, message);
    assertEquals("SUCCEEDED", saved.status());
    assertNotNull(saved.hint());
    assertEquals(saved, service.hint(owner, session, duplicate));
    assertEquals(saved, service.get(owner, session).hint());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_attempts WHERE ai_request_id=?", Integer.class,
        saved.operationId()));
    assertEquals(0, service.get(owner, session).learnerTurns());

    var next = service.send(owner, session, new SendTextRequest("I would like soup.", "hint-next-turn"));
    String newSource = next.messages().getLast().id();
    assertEquals("NOT_STARTED", next.hint().status());
    assertEquals(newSource, next.hint().messageId());
    assertEquals(409, assertThrows(ApiException.class,
        () -> service.hint(owner, session, new HintRequest(newSource, original.clientRequestId()))).status);
    assertEquals(404, assertThrows(ApiException.class,
        () -> service.getHint(owner, session, Long.parseLong(next.messages().get(1).id()))).status);

    provider.timeout = true;
    var uncertain = service.hint(owner, session, new HintRequest(newSource, "hint-with-timeout"));
    long operation = Long.parseLong(uncertain.operationId());
    assertEquals("UNKNOWN", uncertain.status());
    assertEquals("UNKNOWN_ACK_REQUIRED", assertThrows(ApiException.class,
        () -> service.retry(owner, session, operation, new RetryRequest(false))).code);
    assertEquals("SUCCEEDED", service.retry(owner, session, operation, new RetryRequest(true)).hint().status());
    learning.deleteSession(owner, session);
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM assistant_hints WHERE session_id=?", Integer.class, session));
  }

  @Test
  void deletingSessionWhileHintRunsPreventsLatePersistence() throws Exception {
    var owner = owner();
    var opening = service.create(owner, draft());
    long session = Long.parseLong(opening.id());
    var hintRequest = new HintRequest(opening.messages().getFirst().id(), "hint-late");
    provider.entered = new CountDownLatch(1);
    provider.release = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var request = executor.submit(() -> service.hint(owner, session, hintRequest));
      try {
        assertTrue(provider.entered.await(5, TimeUnit.SECONDS));
        learning.deleteSession(owner, session);
      } finally {
        provider.release.countDown();
      }
      var failure = assertThrows(ExecutionException.class, () -> request.get(5, TimeUnit.SECONDS));
      assertEquals(404, assertInstanceOf(ApiException.class, failure.getCause()).status);
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM assistant_hints WHERE session_id=?", Integer.class, session));
    } finally {
      provider.entered = null;
      provider.release = null;
    }
  }
}
