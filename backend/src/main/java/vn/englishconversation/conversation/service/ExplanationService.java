package vn.englishconversation.conversation.service;

import java.util.*;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import vn.englishconversation.ai.config.ChatSettings;
import vn.englishconversation.ai.provider.TextProvider.*;
import vn.englishconversation.ai.service.AiRequestCoordinator;
import vn.englishconversation.ai.service.AiRequestCoordinator.Work;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.conversation.dto.*;
import vn.englishconversation.ai.repository.AiWorkRepository;
import vn.englishconversation.conversation.repository.AssistantHelpRepository;
import vn.englishconversation.conversation.repository.PracticeRepository.Session;
import vn.englishconversation.feedback.dto.*;

import vn.englishconversation.conversation.service.PracticeSessionService;
import vn.englishconversation.conversation.service.ExplanationAnalysis;

@Service
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class ExplanationService {
  private final PracticeSessionService sessions;
  private final AssistantHelpRepository help;
  private final AiWorkRepository aiWork;
  private final AiRequestCoordinator coordinator;
  private final ChatSettings config;
  public ExplanationService(PracticeSessionService sessions, AssistantHelpRepository help, AiWorkRepository aiWork, AiRequestCoordinator coordinator, ChatSettings config) {
    this.sessions = sessions;
    this.help = help;
    this.aiWork = aiWork;
    this.coordinator = coordinator;
    this.config = config;
  }

  public ExplanationResponse getExplanation(AccountPrincipal owner, long id, long message) {
    return sessions.inTransaction().execute(ignored -> {
      sessions.lock(owner, id);
      sessions.assistantSource(id, message);
      sessions.expire(id);
      return explanationView(id, message);
    });
  }

  public ExplanationResponse explain(AccountPrincipal owner, long id, long message, ExplanationRequest input) {
    String fingerprint = sessions.prepareFingerprint("explain|" + message);
    Work work = sessions.reserve(() -> {
      sessions.lockAccount(owner);
      Session session = sessions.lock(owner, id);
      String source = sessions.assistantSource(id, message);
      if (help.conflictingExplanationCount(id, id + ":explain:" + input.clientRequestId(), message) > 0)
        throw sessions.reused();
      sessions.expire(id);
      var existing = help.explanationOperation(id, message);
      if (existing.isPresent()) {
        long operation = existing.get().aiRequestId();
        var view = explanationView(id, message);
        if (!input.retry() || Set.of("RUNNING", "SUCCEEDED").contains(view.status()))
          return Work.replay(id, operation);
        sessions.requireLatestAssistant(session, id, message);
        if (!view.retryable())
          throw sessions.error(409, "RETRY_UNAVAILABLE", "Không thể thử lại phần giải thích.");
        if ("UNKNOWN".equals(view.status()) && !input.acknowledgeUnknown())
          throw sessions.error(409, "UNKNOWN_ACK_REQUIRED", "Hãy xác nhận muốn gọi lại Gemini.");
        if (aiWork.retryDelayCount(operation) > 0) throw sessions.error(429, "RETRY_TOO_EARLY", "Hãy đợi trước khi thử lại.");
        sessions.idle(id, operation);
        sessions.requireProvider();
        return sessions.claim(owner, id, operation, (int) sessions.request(id, operation).attemptCount(),
            ExplanationAnalysis.prompt(session.selectedLevel(), source));
      }
      sessions.requireLatestAssistant(session, id, message);
      if (input.retry()) throw sessions.error(409, "EXPLANATION_NOT_STARTED", "Chưa có yêu cầu giải thích để thử lại.");
      sessions.idle(id, 0);
      sessions.requireProvider();
      long operation = sessions.addRequest(owner, id, "EXPLAIN", "explain:" + input.clientRequestId(),
          fingerprint != null ? fingerprint : sessions.hash("explain|" + message));
      help.createExplanation(id, message, operation);
      return sessions.claim(owner, id, operation, 0, ExplanationAnalysis.prompt(session.selectedLevel(), source));
    });
    if (work.attempt() == 0) return getExplanation(owner, id, message);
    var call = sessions.callProvider(work, ExplanationAnalysis.MAX_JSON_CHARS);
    return coordinator.complete(work, () -> sessions.lockForCompletion(owner, id),
        request -> applyExplanationOutcome(id, message, work, call), () -> explanationView(id, message));
  }

  private void applyExplanationOutcome(long id, long message, Work work, AiRequestCoordinator.ProviderCall call) {
    Outcome outcome = call.outcome();
    if (outcome.success()) {
      try {
        var validated = ExplanationAnalysis.validate(outcome.text(), sessions.assistantSource(id, message));
        help.saveExplanation(validated.simpleEnglish(), validated.meaningVi(), work.request());
      } catch (IllegalArgumentException invalid) {
        outcome = new Outcome(null, outcome.model(), outcome.providerId(), outcome.inputTokens(),
            outcome.outputTokens(), outcome.cacheUsed(), "EXPLANATION_INVALID", false, true, 0);
      }
    }
    sessions.recordOutcome(work, outcome, call.latency());
    if (outcome.unknown()) sessions.releaseUncertainAuxiliary(work.request());
    sessions.touch(id);
  }

  private ExplanationResponse explanationView(long id, long message) {
    return help.explanationState(id, message).map(row -> new ExplanationResponse(Long.toString(message),
            row.unknownAttempt() ? "UNKNOWN" : row.status(), row.errorCode(),
            row.retryable() && row.attemptCount() < config.maxAttempts, row.retryIso(),
            row.simpleEnglish(), row.meaningVi()))
        .orElseGet(() -> new ExplanationResponse(Long.toString(message), "NOT_STARTED", null, false,
            null, null, null));
  }
}
