package vn.englishconversation.ai.service;

import java.math.*;
import java.util.*;
import java.util.function.*;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.englishconversation.ai.config.ChatSettings;
import vn.englishconversation.ai.provider.TextProvider;
import vn.englishconversation.ai.provider.TextProvider.*;
import vn.englishconversation.ai.repository.AiWorkRepository;
import vn.englishconversation.common.exception.ApiException;

/** Gemini request lifecycle. Callbacks own authorization and task data, never provider calls. */
@Service
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class AiRequestCoordinator {
  private final AiAdmissionService admission;
  private final AiWorkRepository aiWork;
  private final TextProvider provider;
  private final ChatSettings config;
  private final TransactionTemplate tx;

  public AiRequestCoordinator(AiAdmissionService admission, AiWorkRepository aiWork,
      TextProvider provider, ChatSettings config, PlatformTransactionManager manager) {
    this.admission = admission;
    this.aiWork = aiWork;
    this.provider = provider;
    this.config = config;
    tx = new TransactionTemplate(manager);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public record Work(long session, long request, int attempt, Prompt prompt) {
    public static Work replay(long session, long request) { return new Work(session, request, 0, null); }
  }
  public record ProviderCall(Outcome outcome, long latency) {}

  public <T> T reserve(Supplier<T> reservation) { return admission.reserve(reservation); }

  /** Caller is inside reserve and has locked owner/session; policy-specific retry checks precede claim. */
  public Work claim(long owner, long session, long operation, int previous, Supplier<Prompt> prompt,
      String feedbackVersion, Runnable onClaim) {
    if (aiWork.globalMinuteAttempts() >= config.globalRpm
        || aiWork.userMinuteAttempts(owner) >= config.userRpm
        || aiWork.runningRequests() >= config.globalConcurrent
        || config.totalAttemptLimit > 0 && aiWork.totalAttempts() >= config.totalAttemptLimit)
      throw new ApiException(429, "AI_QUOTA", "Đã tới hạn mức AI. Hãy thử lại sau.");
    Prompt prepared = prompt.get();
    int attempt = previous + 1;
    aiWork.claimRequest(attempt, config.leaseSeconds, feedbackVersion, operation);
    aiWork.startAttempt(operation, attempt, provider.model());
    onClaim.run();
    return new Work(session, operation, attempt, prepared);
  }

  /** Provider always runs between reservation and completion, outside any active transaction. */
  public ProviderCall invoke(Work work, int maxChars) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Provider must run outside a transaction");
    if (work.attempt() == 0) throw new IllegalStateException("Cannot dispatch a replay");
    long started = System.nanoTime();
    Outcome outcome;
    try { outcome = provider.generate(work.prompt()); }
    catch (RuntimeException failure) { outcome = Outcome.failure("PROVIDER_UNEXPECTED", true, false, 0); }
    if (outcome.success() && !validText(outcome.text(), maxChars))
      outcome = Outcome.failure("PROVIDER_INVALID_TEXT", false, false, 0);
    return new ProviderCall(outcome, (System.nanoTime() - started) / 1_000_000);
  }

  /** Lock/expire callback runs first; deleted or superseded work can never reach the write callback. */
  public <T> T complete(Work work, Supplier<Boolean> lockAndCheck,
      Consumer<AiWorkRepository.RequestState> onCurrent, Supplier<T> view) {
    return tx.execute(ignored -> {
      boolean eligible = lockAndCheck.get();
      var request = aiWork.request(work.session(), work.request()).orElseThrow(() ->
          new ApiException(404, "OPERATION_NOT_FOUND", "Không tìm thấy tác vụ."));
      if (eligible && "RUNNING".equals(request.status()) && request.attemptCount() == work.attempt())
        onCurrent.accept(request);
      return view.get();
    });
  }

  /** Called with the session locked. Task callback updates its own draft/turn/feedback after expiry. */
  public void expire(long session, Consumer<AiWorkRepository.ExpiredRequest> onExpired) {
    var expired = aiWork.expiredRequest(session);
    if (expired.isEmpty()) return;
    var request = expired.get();
    aiWork.expireAttempt(request.id(), request.attemptCount());
    if (Set.of("TRANSCRIPTION", "EXPLAIN", "HINT").contains(request.taskType()))
      aiWork.expireAuxiliaryRequest(request.id());
    else aiWork.expireRequest(request.id());
    onExpired.accept(request);
  }

  public long createRequest(long owner, long session, String task, String key,
      String fingerprint, String version) {
    return aiWork.createRequest(owner, session, task, key, fingerprint, version);
  }

  public void releaseUncertain(long operation) { aiWork.releaseUncertain(operation); }
  public void skipRequest(long operation) { aiWork.skipRequest(operation); }
  public void closeUncertainChat(long session) { aiWork.closeUncertainChat(session); }

  public void requireProvider() {
    if (!provider.ready() || !config.fingerprintsReady())
      throw new ApiException(503, "AI_NOT_CONFIGURED", "Hội thoại AI chưa được cấu hình.");
  }

  private static boolean validText(String text, int limit) {
    return !text.isBlank() && text.length() <= limit
        && text.codePoints().noneMatch(c -> Character.isISOControl(c) && c != 10 && c != 13 && c != 9);
  }

  public String recordOutcome(Work work, Outcome result, long latency) {
    String status = result.success() ? "SUCCEEDED" : result.unknown() ? "UNKNOWN" : "FAILED";
    BigDecimal cost = work.prompt().waveAudio() == null ? estimate(result) : null;
    aiWork.completeAttempt(status, result.model(), result.providerId(), result.inputTokens(), result.outputTokens(), latency, cost, cost == null ? null : config.pricingReference, result.errorCode(), work.request(), work.attempt());
    aiWork.completeRequest(status, result.retryable(), result.errorCode(), result.retryAfterSeconds(), work.request());
    return status;
  }

  private BigDecimal estimate(Outcome result) {
    if (result.cacheUsed()
        || result.inputTokens() == null
        || result.outputTokens() == null
        || !Objects.equals(provider.model(), result.model())
        || config.inputPrice == null
        || config.outputPrice == null
        || config.pricingReference.isBlank()
        || config.inputPrice.signum() < 0
        || config.outputPrice.signum() < 0) return null;
    return config
        .inputPrice
        .multiply(BigDecimal.valueOf(result.inputTokens()))
        .add(config.outputPrice.multiply(BigDecimal.valueOf(result.outputTokens())))
        .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
  }

}
