package vn.englishconversation.pronunciation.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.pronunciation.repository.ConversationPronunciationRepository;
import vn.englishconversation.pronunciation.dto.PronunciationData.Word;
import vn.englishconversation.pronunciation.repository.PronunciationRepository;
import vn.englishconversation.voice.service.VoiceStorage;
import vn.englishconversation.voice.service.WaveAudio;

@Service
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class ConversationPronunciation {
  private final ConversationPronunciationRepository persistence;
  private final PronunciationService scoring;
  private final PronunciationAudio files;
  private final VoiceStorage voice;
  private final ObjectMapper json;
  private final TransactionTemplate tx;
  private final PronunciationRepository repository;
  private final boolean enabled;

  public ConversationPronunciation(ConversationPronunciationRepository persistence, PronunciationService scoring, PronunciationAudio files,
      VoiceStorage voice, ObjectMapper json, TransactionTemplate tx, PronunciationRepository repository,
      @Value("${app.pronunciation.conversation-enabled:false}") boolean enabled) {
    this.persistence = persistence;
    this.scoring = scoring;
    this.files = files;
    this.voice = voice;
    this.json = json;
    this.tx = tx;
    this.repository = repository;
    this.enabled = enabled;
  }

  public boolean available() {
    return enabled && scoring.scoringAvailable();
  }

  public void schedule(long session, long audioId, boolean requested) {
    ConversationPronunciationRepository.RawAudio raw = persistence.lockConfirmedAudio(audioId, session).orElseThrow();
    String reason = ineligible(raw, requested);
    setPreparation(audioId, reason == null ? "PENDING_PREPARATION" : "SKIPPED", reason);
  }

  private String ineligible(ConversationPronunciationRepository.RawAudio raw, boolean requested) {
    if (raw.durationMs() > WaveAudio.CONVERSATION_MAX_SECONDS * 1000) return "TOO_LONG";
    if (!available()) return "ASSESSMENT_DISABLED";
    if (!requested) return "ASSESSMENT_NOT_REQUESTED";
    if (raw.expired()) return "AUDIO_EXPIRED";
    if (raw.storageKey() == null) return "AUDIO_MISSING";
    return null;
  }

  @Scheduled(fixedDelayString = "${app.pronunciation.poll-ms:2000}")
  public void preparePending() {
    for (var candidate : persistence.pendingPreparations()) {
      long audioId = candidate.id();
      long session = candidate.sessionId();
      long user = candidate.userId();
      try {
        tx.executeWithoutResult(ignored -> prepare(user, session, audioId));
      } catch (DataAccessException failure) {
        persistence.failPreparation(audioId, session);
      }
    }
  }

  private void prepare(long user, long session, long audioId) {
    if (!repository.lockActiveUser(user)) {
      persistence.skipPreparation(audioId, session);
      return;
    }
    ConversationPronunciationRepository.RawAudio raw = persistence.lockPendingAudio(audioId, session).orElse(null);
    if (raw == null) return;
    String reason = ineligible(raw, true);
    if (reason != null) {
      setPreparation(audioId, "SKIPPED", reason);
      return;
    }
    String key;
    try {
      key = files.save(Files.readAllBytes(voice.path(raw.storageKey())));
    } catch (NoSuchFileException missing) {
      setPreparation(audioId, "FAILED", "AUDIO_MISSING");
      return;
    } catch (IOException | IllegalStateException unavailable) {
      setPreparation(audioId, "FAILED", "PREPARATION_FAILED");
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) files.remove(key);
      }
    });
    String operation = "voice:" + audioId;
    long request = persistence.createRequest(user, session, operation, raw.requestFingerprint());
    persistence.createAttempt(user, request, operation, key, audioId);
    setPreparation(audioId, "PREPARED", null);
  }

  private void setPreparation(long audioId, String status, String reason) {
    persistence.setPreparation(status, reason, audioId);
  }

  public record Score(String status, String errorCode, BigDecimal overall, BigDecimal accuracy, BigDecimal fluency,
                      String recognizedText, List<Word> words) {}

  public Map<Long, Score> scores(long session) {
    return persistence.scores(session).stream().collect(Collectors.toMap(row -> row.id(), row -> {
      boolean scored = "SUCCEEDED".equals(row.status());
      String detail = scored ? row.detailJson() : null;
      return new Score(row.status(), row.errorCode(),
          row.overallScore(), row.accuracy(),
          row.fluency(), scored ? row.recognizedText() : null,
          detail == null ? List.of() : List.of(json.readValue(detail, Word[].class)));
    }));
  }
}
