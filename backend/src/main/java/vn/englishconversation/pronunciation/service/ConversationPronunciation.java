package vn.englishconversation.pronunciation.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.pronunciation.dto.PronunciationData.Word;
import vn.englishconversation.voice.service.VoiceStorage;

@Service
@Profile("g6")
public class ConversationPronunciation {
  private final JdbcStore db;
  private final PronunciationService scoring;
  private final PronunciationAudio files;
  private final VoiceStorage voice;
  private final ObjectMapper json;
  private final boolean enabled;

  public ConversationPronunciation(JdbcStore db, PronunciationService scoring, PronunciationAudio files,
      VoiceStorage voice, ObjectMapper json,
      @Value("${app.pronunciation.conversation-enabled:false}") boolean enabled) {
    this.db = db;
    this.scoring = scoring;
    this.files = files;
    this.voice = voice;
    this.json = json;
    this.enabled = enabled;
  }

  public boolean available() {
    return enabled && scoring.scoringAvailable();
  }

  // Runs inside the transcript confirmation transaction with the owner and session locked.
  public void enqueue(long user, long session, long audioId) {
    if (!available()) return;
    var raw = db.find("""
        SELECT storage_key,request_fingerprint,duration_ms FROM audio_submissions
        WHERE id=? AND session_id=? AND status='CONFIRMED' AND storage_key IS NOT NULL
          AND expires_at>UTC_TIMESTAMP(6) FOR UPDATE
        """, audioId, session);
    if (raw.isEmpty() || raw.get().number("duration_ms") > 30_000) return;
    String key;
    try {
      key = files.save(Files.readAllBytes(voice.path(raw.get().text("storage_key"))));
    } catch (IOException | IllegalStateException unavailable) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) files.remove(key);
      }
    });
    String operation = "voice:" + audioId;
    long request = db.insert("""
        INSERT INTO ai_requests(user_id,session_id,task_type,operation_key,request_fingerprint,prompt_version,created_at)
        VALUES (?,?,'PRONUNCIATION',?,?,'azure-en-us-unscripted-v1',UTC_TIMESTAMP(6))
        """, user, session, operation, raw.get().text("request_fingerprint"));
    db.insert("""
        INSERT INTO pronunciation_attempts(user_id,ai_request_id,client_request_id,mode,status,
          provider,locale,assessment_version,audio_submission_id,audio_storage_key,audio_expires_at,created_at)
        SELECT ?,?,?,'UNSCRIPTED','PENDING','AZURE_SPEECH','en-US','azure-en-us-unscripted-v1',id,?,
          expires_at,UTC_TIMESTAMP(6) FROM audio_submissions WHERE id=?
        """, user, request, operation, key, audioId);
  }

  public record Score(String status, String errorCode, BigDecimal overall, BigDecimal accuracy, BigDecimal fluency,
                      String recognizedText, List<Word> words) {}

  public Map<Long, Score> scores(long session) {
    return db.rows("""
        SELECT m.id,p.status,p.error_code,p.overall_score,p.accuracy,p.fluency,
          p.recognized_text,p.detail_json FROM messages m
        JOIN pronunciation_attempts p ON p.audio_submission_id=m.audio_submission_id WHERE m.session_id=?
        """, session).stream().collect(Collectors.toMap(row -> row.number("id"), row -> {
      boolean scored = "SUCCEEDED".equals(row.text("status"));
      String detail = scored ? row.text("detail_json") : null;
      return new Score(row.text("status"), row.text("error_code"),
          (BigDecimal) row.data().get("overall_score"), (BigDecimal) row.data().get("accuracy"),
          (BigDecimal) row.data().get("fluency"), scored ? row.text("recognized_text") : null,
          detail == null ? List.of() : List.of(json.readValue(detail, Word[].class)));
    }));
  }
}
