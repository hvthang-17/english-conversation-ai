package vn.englishconversation.feedback.service;

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
import vn.englishconversation.conversation.repository.PracticeRepository.Session;
import vn.englishconversation.feedback.dto.*;
import vn.englishconversation.feedback.repository.FeedbackStore;
import vn.englishconversation.feedback.service.FeedbackAnalysis;
import vn.englishconversation.feedback.service.FeedbackAnswerGuide;
import vn.englishconversation.feedback.service.FeedbackValidationService;

import vn.englishconversation.conversation.service.PracticeSessionService;

@Service
@FeatureEnabled(FeatureEnabled.Feature.FEEDBACK)
public class FeedbackService {
  private final PracticeSessionService sessions;
  private final AiWorkRepository aiWork;
  private final AiRequestCoordinator coordinator;
  private final ChatSettings config;
  private final FeedbackValidationService feedbackValidation;
  private final FeedbackStore feedback;
  private final FeedbackAnalysis analysis;
  public FeedbackService(PracticeSessionService sessions, AiWorkRepository aiWork, AiRequestCoordinator coordinator, ChatSettings config, FeedbackValidationService feedbackValidation, FeedbackStore feedback, FeedbackAnalysis analysis) {
    this.sessions = sessions;
    this.aiWork = aiWork;
    this.coordinator = coordinator;
    this.config = config;
    this.feedbackValidation = feedbackValidation;
    this.feedback = feedback;
    this.analysis = analysis;
  }

  public FeedbackResponse getFeedback(AccountPrincipal owner, long id) {
    return sessions.inTransaction().execute(
        status -> {
          sessions.lock(owner, id);
          sessions.expire(id);
          var review = feedback.view(id, config.maxAttempts);
          if (!FeedbackAnswerGuide.needsFrame(review.result())) return review;
          return new FeedbackResponse(review.status(), review.errorCode(), review.retryable(), review.attempts(),
              FeedbackAnswerGuide.withFrames(review.result(), sessions.messages(id)), review.suggestionIds(),
              review.repeatedErrors());
        });
  }

  public FeedbackResponse generateFeedback(
      AccountPrincipal owner, long id, boolean retry, boolean acknowledgeUnknown) {
    String fingerprint = sessions.prepareFingerprint("feedback|" + id);
    Work work =
        sessions.reserve(
            () -> {
              sessions.lockAccount(owner);
              Session session = sessions.lock(owner, id);
              if (!"COMPLETED".equals(session.status()))
                throw sessions.error(
                    409,
                    "PRACTICE_NOT_COMPLETED",
                    "Hãy hoàn thành buổi luyện trước khi yêu cầu nhận xét.");
              sessions.expire(id);
              var existing =
                  feedback.feedbackOperation(id);
              if (existing.isPresent()) {
                long operation = existing.get().aiRequestId();
                var request = sessions.request(id, operation);
                String status = request.status();
                if (!retry || Set.of("RUNNING", "SUCCEEDED").contains(status))
                  return Work.replay(id, operation);
                int attempt = (int) request.attemptCount();
                boolean validatorRecovery = "FAILED".equals(status) && FeedbackAnalysis.canRetryAfterValidatorUpdate(
                    request.promptVersion(), request.errorCode(), attempt, config.maxAttempts);
                if ((!validatorRecovery && attempt >= config.maxAttempts)
                    || !("UNKNOWN".equals(status)
                        || "FAILED".equals(status) && (request.retryable()
                            || validatorRecovery
                            || FeedbackAnalysis.canRetryRejectedV3(request.promptVersion(), request.errorCode()))))
                  throw sessions.error(
                      409,
                      "RETRY_UNAVAILABLE",
                      "Đã hết số lần thử lại hoặc lỗi cần quản trị viên xử lý.");
                if ("UNKNOWN".equals(status) && !acknowledgeUnknown)
                  throw sessions.error(409, "UNKNOWN_ACK_REQUIRED", "Hãy xác nhận muốn gọi lại Gemini.");
                if (aiWork.retryDelayCount(operation)
                    > 0) throw sessions.error(429, "RETRY_TOO_EARLY", "Hãy đợi trước khi thử lại.");
                sessions.idle(id, operation);
                sessions.requireProvider();
                return sessions.claim(owner, id, operation, attempt, prompt(id));
              }
              if (retry) throw sessions.error(409, "FEEDBACK_NOT_STARTED", "Hãy tạo nhận xét trước.");
              sessions.requireProvider();
              // Ending a practice makes uncertain chat attempts terminal for admission. Their
              // attempt
              // history and the entire transcript are still available.
              coordinator.closeUncertainChat(id);
              sessions.idle(id, 0);
              long operation = sessions.addRequest(owner, id, "FEEDBACK", "review",
                  fingerprint != null ? fingerprint : sessions.hash("feedback|" + id));
              feedback.create(id, operation);
              return sessions.claim(owner, id, operation, 0, prompt(id));
            });
    if (work.attempt() == 0) return getFeedback(owner, id);

    var call = sessions.callProvider(work, FeedbackAnalysis.MAX_JSON_CHARS);
    Outcome outcome = call.outcome();
    FeedbackResult result = null;
    if (outcome.success()) {
      try {
        result = feedbackValidation.validate(outcome.text(), sessions.messages(id), work.prompt());
      } catch (IllegalArgumentException invalid) {
        outcome =
            new Outcome(
                null,
                outcome.model(),
                outcome.providerId(),
                outcome.inputTokens(),
                outcome.outputTokens(),
                outcome.cacheUsed(),
                "FEEDBACK_INVALID",
                false,
                true,
                0);
      }
    }
    Outcome finalOutcome = outcome;
    FeedbackResult validated = result;
    return coordinator.complete(work, () -> sessions.lockForCompletion(owner, id),
        request -> applyFeedbackOutcome(id, work, finalOutcome, validated, call.latency()),
        () -> feedback.view(id, config.maxAttempts));
  }

  private void applyFeedbackOutcome(long id, Work work, Outcome finalOutcome,
      FeedbackResult validated, long latency) {
    String outcomeStatus = sessions.recordOutcome(work, finalOutcome, latency);
    if (validated != null) feedback.save(id, work.request(), validated);
    else feedback.setStatus(work.request(), outcomeStatus);
  }

  private Prompt prompt(long id) {
    var session = sessions.session(id);
    var snapshot = sessions.snapshot(session);
    try {
      return analysis.prompt(session.selectedLevel(), snapshot.title(), snapshot.goal(),
          snapshot.learnerRole(), snapshot.aiRole(), snapshot.context(), session.mode(),
          sessions.messages(id), config.contextLimit);
    } catch (IllegalArgumentException tooLong) {
      throw sessions.error(422, "CONTEXT_LIMIT", "Nội dung buổi vượt giới hạn phân tích.");
    }
  }
}
