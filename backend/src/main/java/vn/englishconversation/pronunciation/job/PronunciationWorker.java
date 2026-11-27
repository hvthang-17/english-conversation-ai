package vn.englishconversation.pronunciation.job;

import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.englishconversation.pronunciation.dto.PronunciationData.Assessment;
import vn.englishconversation.pronunciation.repository.PronunciationRepository;
import vn.englishconversation.pronunciation.repository.PronunciationRepository.Job;
import vn.englishconversation.pronunciation.service.PronunciationAudio;
import vn.englishconversation.pronunciation.service.PronunciationProvider;
import vn.englishconversation.pronunciation.service.PronunciationService;

@Component
@Profile({"g5", "g6"})
public class PronunciationWorker {
  private final PronunciationRepository store;
  private final PronunciationAudio audio;
  private final PronunciationProvider provider;
  private final PronunciationService service;
  private final TransactionTemplate tx;
  private final java.util.Optional<vn.englishconversation.learning.service.ProgressService> progress;

  public PronunciationWorker(PronunciationRepository store, PronunciationAudio audio, PronunciationProvider provider,
      PronunciationService service, TransactionTemplate tx,
      java.util.Optional<vn.englishconversation.learning.service.ProgressService> progress) {
    this.store = store;
    this.audio = audio;
    this.provider = provider;
    this.service = service;
    this.tx = tx;
    this.progress = progress;
  }

  @Scheduled(fixedDelayString = "${app.pronunciation.poll-ms:2000}")
  public void assessPending() {
    if (!service.scoringAvailable()) return;
    for (var candidate : store.candidates()) {
      long user = candidate.number("user_id");
      var job = tx.execute(transaction -> store.lockActiveUser(user)
          ? store.claim(candidate.number("id"), user, service::reserveProviderCall).orElse(null) : null);
      if (job != null) {
        assess(job);
        return;
      }
    }
  }

  private void assess(Job job) {
    long start = System.nanoTime();
    Assessment result = null;
    String status;
    String error = null;
    boolean retry = false;
    try {
      var path = audio.path(job.audio());
      if (!Files.isRegularFile(path)) throw new PronunciationProvider.Failure("AUDIO_UNAVAILABLE", false, false);
      result = provider.assess(path, job.reference(), job.locale());
      if (result != null && job.reference() == null) result = new Assessment(result.recognizedText(), result.overall(), result.accuracy(), result.fluency(), null, result.words());
      status = result == null ? "INSUFFICIENT_EVIDENCE" : "SUCCEEDED";
      if (result == null) error = "NO_SPEECH_EVIDENCE";
    } catch (PronunciationProvider.Failure failure) {
      status = failure.unknown() ? "UNKNOWN" : "FAILED";
      error = failure.getMessage();
      retry = failure.retryable() && job.run() < 2;
    } catch (RuntimeException invalid) {
      status = "UNKNOWN";
      error = "AZURE_OUTCOME_UNKNOWN";
    }
    long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    Assessment score = result;
    String finalStatus = status;
    String finalError = error;
    boolean scheduleRetry = retry;
    tx.executeWithoutResult(transaction -> {
      if (store.lockActiveUser(job.user()) && store.ownsClaim(job)) {
        store.complete(job, score, finalStatus, finalError, scheduleRetry, latency);
        if ("SUCCEEDED".equals(finalStatus)) progress.ifPresent(service -> service.pronunciationScored(job.user(), job.id()));

      }
    });
    cleanupAttempt(job.id(), job.user());
  }

  @Scheduled(fixedDelayString = "${app.pronunciation.cleanup-ms:30000}")
  public void cleanup() {
    for (var row : store.cleanupCandidates()) {
      cleanupAttempt(row.number("id"), row.number("user_id"));
    }
    store.pruneTechnicalAttempts();
    audio.removeOrphans(Instant.now().minus(1, ChronoUnit.HOURS), store::hasAudio);
  }

  private void cleanupAttempt(long id, long user) {
    tx.executeWithoutResult(transaction -> {
      if (!store.lockUser(user)) return;
      store.expire(id);
      store.terminalAudio(id).filter(audio::remove).ifPresent(key -> store.audioRemoved(id));
    });
  }
}
