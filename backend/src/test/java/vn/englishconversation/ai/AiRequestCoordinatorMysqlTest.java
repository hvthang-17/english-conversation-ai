package vn.englishconversation.ai;


import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.conversation.dto.*;
import vn.englishconversation.voice.service.WaveAudio;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class AiRequestCoordinatorMysqlTest extends FullSystemMysqlFixture {

  @Test
  void allGeminiTaskKindsPersistUsageAndKeepProviderOutsideTransactions() {
    var owner = owner();
    long session = Long.parseLong(service.create(owner, draft()).id());
    var sent = service.send(owner, session, new SendTextRequest("Soup, please.", "coordinator-usage-turn"));
    long message = Long.parseLong(sent.messages().getLast().id());
    service.hint(owner, session, new HintRequest(Long.toString(message), "coordinator-usage-hint"));
    service.explain(owner, session, message, new ExplanationRequest("coordinator-usage-explain", false, false));
    service.finish(owner, session);
    provider.feedbackOutput = CLEAN_REVIEW;
    assertEquals("SUCCEEDED", service.generateFeedback(owner, session, false, false).status());
    long voice = Long.parseLong(service.create(owner, voicePractice()).id());
    assertEquals("READY", service.uploadVoice(owner, voice, "coordinator-usage-audio",
        WaveAudio.read(wave(160, 500, 1)), false).status());
    var kinds = new java.util.HashSet<String>();
    for (var row : jdbc.queryForList("""
        SELECT r.task_type,r.status AS request_status,a.status AS attempt_status,
          a.input_tokens,a.output_tokens,a.latency_ms FROM ai_requests r
        JOIN ai_attempts a ON a.ai_request_id=r.id WHERE r.user_id=?
        """, owner.id())) {
      kinds.add((String) row.get("task_type"));
      assertEquals("SUCCEEDED", row.get("request_status"));
      assertEquals("SUCCEEDED", row.get("attempt_status"));
      assertTrue(((Number) row.get("input_tokens")).longValue() > 0);
      assertTrue(((Number) row.get("output_tokens")).longValue() > 0);
      assertNotNull(row.get("latency_ms"));
    }
    assertEquals(java.util.Set.of("CHAT", "HINT", "EXPLAIN", "FEEDBACK", "TRANSCRIPTION"), kinds);
  }

  @Test
  void expiredLeaseFencesLateCompletionAndRetryLimitStillApplies() throws Exception {
    var owner = owner();
    long session = Long.parseLong(service.create(owner, draft()).id());
    provider.entered = new CountDownLatch(1);
    provider.release = new CountDownLatch(1);
    long operation;
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var pending = executor.submit(() -> service.send(owner, session,
          new SendTextRequest("Soup, please.", "coordinator-expired-turn")));
      try {
        assertTrue(provider.entered.await(5, TimeUnit.SECONDS));
        operation = jdbc.queryForObject("SELECT id FROM ai_requests WHERE session_id=? AND status='RUNNING'", Long.class, session);
        jdbc.update("UPDATE ai_requests SET processing_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?", operation);
        assertEquals("UNKNOWN", service.get(owner, session).operation().status());
      } finally { provider.release.countDown(); }
      assertEquals(2, pending.get(5, TimeUnit.SECONDS).messages().size());
    } finally { provider.entered = null; provider.release = null; }
    assertEquals("LEASE_EXPIRED", jdbc.queryForObject(
        "SELECT error_code FROM ai_attempts WHERE ai_request_id=? AND attempt_no=1", String.class, operation));
    provider.timeout = true;
    assertEquals("UNKNOWN", service.retry(owner, session, operation, new RetryRequest(true)).operation().status());
    assertEquals("RETRY_LIMIT", assertThrows(ApiException.class,
        () -> service.retry(owner, session, operation, new RetryRequest(true))).code);
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ai_attempts WHERE ai_request_id=?", Integer.class, operation));
  }
}
