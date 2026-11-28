package vn.englishconversation.conversation.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import vn.englishconversation.ai.config.ChatSettings;
import vn.englishconversation.ai.repository.AiWorkRepository;
import vn.englishconversation.ai.service.AiRequestCoordinator;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.conversation.dto.*;
import vn.englishconversation.conversation.repository.*;
import vn.englishconversation.feedback.repository.FeedbackStore;
import vn.englishconversation.pronunciation.service.ConversationPronunciation;
import vn.englishconversation.scenario.repository.PracticeScenarioRepository;
import vn.englishconversation.voice.repository.VoiceDraftRepository;

class PracticeReservationPreparationTest {
  @Test
  void createReplayStillWorksWithoutFingerprintConfiguration() {
    var fixture = new Fixture("");
    when(fixture.practices.priorSession(1,"existing-create"))
        .thenReturn(Optional.of(new PracticeRepository.PriorSession(7,1,"A2","TEXT","DEFAULT")));
    when(fixture.practices.opening(7)).thenReturn(9L);
    assertSame(fixture.response, fixture.sessions.create(fixture.owner,
        new CreatePracticeRequest("1","A2","existing-create")));
    verify(fixture.sessions, never()).hash(anyString());
    verify(fixture.coordinator, never()).requireProvider();
    verify(fixture.sessions).execute(fixture.owner, AiRequestCoordinator.Work.replay(7,9));
  }

  @Test
  void textReplayPreparesNormalizedFingerprintOutsideReservation() {
    var fixture = new Fixture("synthetic-fingerprint-key-only-1234567890");
    String fingerprint = fixture.sessions.hash("text|Hello");
    clearInvocations(fixture.sessions);
    doAnswer(call -> {
      assertFalse(fixture.reserving.get(), "Pure HMAC preparation must not hold admission");
      return call.callRealMethod();
    }).when(fixture.sessions).hash(anyString());
    when(fixture.practices.ownedSession(7,1,true)).thenReturn(Optional.of(mock(PracticeRepository.Session.class)));
    when(fixture.practices.priorTurn(7,"existing-text"))
        .thenReturn(Optional.of(new PracticeRepository.PriorTurn(9,"LEARNER_TEXT",fingerprint)));
    assertSame(fixture.response, fixture.sessions.send(fixture.owner,7,new SendTextRequest("  Hello  ","existing-text")));
    verify(fixture.sessions, times(1)).hash("text|Hello");
    verify(fixture.sessions).execute(fixture.owner, AiRequestCoordinator.Work.replay(7,9));
    verify(fixture.coordinator, never()).requireProvider();
  }

  private static class Fixture {
    final PracticeRepository practices = mock(PracticeRepository.class);
    final AiRequestCoordinator coordinator = mock(AiRequestCoordinator.class);
    final AccountPrincipal owner = new AccountPrincipal(1L,0);
    final PracticeResponse response = mock(PracticeResponse.class);
    final AtomicBoolean reserving = new AtomicBoolean();
    final PracticeSessionService sessions;

    Fixture(String secret) {
      sessions = spy(new PracticeSessionService(mock(PracticeScenarioRepository.class), mock(VoiceDraftRepository.class),
          mock(AssistantHelpRepository.class), mock(AiWorkRepository.class), practices, coordinator,
          new ChatSettings(new MockEnvironment().withProperty("CHAT_FINGERPRINT_SECRET",secret)),
          mock(PlatformTransactionManager.class), mock(FeedbackStore.class), mock(ConversationPronunciation.class)));
      doAnswer(call -> {
        reserving.set(true);
        try { return ((Supplier<?>)call.getArgument(0)).get(); }
        finally { reserving.set(false); }
      }).when(coordinator).reserve(any());
      when(practices.lockActiveAccount(1,0)).thenAnswer(call -> {
        assertTrue(reserving.get(), "Owner/replay checks must remain in reservation"); return true;
      });
      doReturn(response).when(sessions).execute(eq(owner),any());
    }
  }
}
