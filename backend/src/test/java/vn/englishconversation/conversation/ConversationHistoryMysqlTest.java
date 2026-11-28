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
class ConversationHistoryMysqlTest extends FullSystemMysqlFixture {

  @Test
  void historyPaginationUsesTimestampThenIdAndBulkDeletionRequiresTransaction() {
    var owner = owner();
    var other = owner();
    long first = Long.parseLong(service.create(owner, draft()).id());
    long second = Long.parseLong(service.create(owner, draft()).id());
    long third = Long.parseLong(service.create(owner, draft()).id());
    jdbc.update("UPDATE practice_sessions SET started_at='2026-10-01 01:00:00' WHERE id IN (?,?)", first, second);
    jdbc.update("UPDATE practice_sessions SET started_at='2026-10-02 01:00:00' WHERE id=?", third);
    var page = learning.history(owner, 0, 2);
    assertEquals(3, page.total());
    assertEquals(List.of(Long.toString(third), Long.toString(second)), page.items().stream().map(item -> item.id()).toList());
    assertEquals(Long.toString(first), learning.history(owner, 1, 2).items().getFirst().id());
    assertTrue(learning.history(owner, 2, 2).items().isEmpty());
    assertEquals(0, learning.history(other, 0, 2).total());
    assertEquals(400, assertThrows(ApiException.class, () -> learning.history(owner, -1, 2)).status);
    assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
        () -> historyService.clearLocked(owner.id()));
    assertEquals(3, learning.history(owner, 0, 2).total());
    learning.deleteAll(owner);
    assertEquals(0, learning.history(owner, 0, 2).total());
  }

  @Test
  void deletingWhileProviderRunsDiscardsLateResultAndDetachesMetadata() throws Exception {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    provider.entered = new CountDownLatch(1);
    provider.release = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var pending =
          executor.submit(
              () ->
                  service.send(
                      owner, id, new SendTextRequest("Soup, please.", "late-after-delete")));
      try {
        assertTrue(provider.entered.await(5, TimeUnit.SECONDS));
        learning.deleteSession(owner, id);
      } finally {
        provider.release.countDown();
      }
      var failure = assertThrows(ExecutionException.class, () -> pending.get(6, TimeUnit.SECONDS));
      assertEquals(404, assertInstanceOf(ApiException.class, failure.getCause()).status);
    } finally {
      provider.entered = null;
      provider.release = null;
    }
    assertEquals(
        0,
        jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE session_id=?", Integer.class, id));
    assertEquals(
        1,
        jdbc.queryForObject(
            """
SELECT COUNT(*) FROM ai_attempts a JOIN ai_requests r ON r.id=a.ai_request_id
WHERE r.user_id=? AND r.session_id IS NULL AND a.status='UNKNOWN' AND a.error_code='PRACTICE_DELETED'
""",
            Integer.class,
            owner.id()));
  }
}
