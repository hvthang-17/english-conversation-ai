package vn.englishconversation.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.englishconversation.ai.config.ChatSettings;
import vn.englishconversation.ai.provider.TextProvider;
import vn.englishconversation.ai.provider.TextProvider.*;
import vn.englishconversation.ai.repository.AiWorkRepository;
import vn.englishconversation.common.exception.ApiException;

class AiRequestCoordinatorTest {
  final AiWorkRepository store = mock(AiWorkRepository.class);
  final TextProvider provider = mock(TextProvider.class);
  final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
  final ChatSettings config = new ChatSettings(new MockEnvironment());
  final AiRequestCoordinator coordinator = new AiRequestCoordinator(
      mock(AiAdmissionService.class), store, provider, config, manager);
  final AiRequestCoordinator.Work work = new AiRequestCoordinator.Work(1, 2, 1,
      new Prompt("offline", List.of(), 64));

  @Test
  void refusesProviderInsideTransactionAndDoesNotDispatchReplays() {
    boolean previous = TransactionSynchronizationManager.isActualTransactionActive();
    try {
      TransactionSynchronizationManager.setActualTransactionActive(true);
      assertThrows(IllegalStateException.class, () -> coordinator.invoke(work, 20));
    } finally { TransactionSynchronizationManager.setActualTransactionActive(previous); }
    assertThrows(IllegalStateException.class, () -> coordinator.invoke(AiRequestCoordinator.Work.replay(1, 2), 20));
    verifyNoInteractions(provider);
  }

  @Test
  void normalizesProviderExceptionAndInvalidText() {
    when(provider.generate(any())).thenThrow(new IllegalStateException("synthetic"));
    assertEquals("PROVIDER_UNEXPECTED", coordinator.invoke(work, 20).outcome().errorCode());
    doReturn(new Outcome(" ", "fixture", null, 1L, 1L, false, null, false, false, 0))
        .when(provider).generate(any());
    assertEquals("PROVIDER_INVALID_TEXT", coordinator.invoke(work, 20).outcome().errorCode());
  }

  @Test
  void quotaRejectsBeforePromptAndAttemptCreation() {
    when(store.globalMinuteAttempts()).thenReturn((long) config.globalRpm);
    var prompt = new AtomicInteger();
    assertEquals("AI_QUOTA", assertThrows(ApiException.class, () -> coordinator.claim(1, 1, 2, 0,
        () -> { prompt.incrementAndGet(); return work.prompt(); }, "fixture", () -> fail("claim callback"))).code);
    assertEquals(0, prompt.get());
    verify(store, never()).claimRequest(anyInt(), anyInt(), anyString(), anyLong());
  }

  @Test
  void completionFencesAttemptAndRunsViewWithoutCallingTaskForStaleWork() {
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(store.request(1, 2)).thenReturn(Optional.of(new AiWorkRepository.RequestState(
        2, "CHAT", "RUNNING", null, false, 2, "fixture")));
    var writes = new AtomicInteger();
    assertEquals("stale", coordinator.complete(work, () -> true,
        request -> writes.incrementAndGet(), () -> "stale"));
    assertEquals(0, writes.get());
    verify(manager).commit(any());
    reset(store);
    when(store.request(1, 2)).thenReturn(Optional.of(new AiWorkRepository.RequestState(
        2, "CHAT", "RUNNING", null, false, 1, "fixture")));
    assertEquals("closed", coordinator.complete(work, () -> false,
        request -> writes.incrementAndGet(), () -> "closed"));
    assertEquals(0, writes.get());
  }
}
