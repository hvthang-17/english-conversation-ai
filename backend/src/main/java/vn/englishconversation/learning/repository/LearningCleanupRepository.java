package vn.englishconversation.learning.repository;

import org.springframework.stereotype.Repository;
import vn.englishconversation.config.features.FeatureEnabled;
import vn.englishconversation.common.repository.JdbcStore;

/** Persistence only: caller retains transaction, lock ordering, claim/version and cleanup policy. */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.LEARNING)
public class LearningCleanupRepository {
  private final JdbcStore db;
  public LearningCleanupRepository(JdbcStore db) { this.db = db; }
  public record AudioKey(String storageKey) {}
  public java.util.List<AudioKey> pendingAudio(long owner) {
    return db.rows("SELECT a.storage_key FROM audio_submissions a JOIN ai_requests r ON"
                + " r.id=a.ai_request_id WHERE r.user_id=? AND a.storage_key IS NOT NULL", owner).stream().map(row -> new AudioKey(row.text("storage_key"))).toList();
  }

  public long pendingAudioCount(long owner) {
    return db.count("SELECT COUNT(*) FROM audio_submissions a JOIN ai_requests r ON r.id=a.ai_request_id"
                + " WHERE r.user_id=? AND a.storage_key IS NOT NULL", owner);
  }

  public void deleteAudio(long owner) {
    db.update("DELETE a FROM audio_submissions a JOIN ai_requests r ON r.id=a.ai_request_id WHERE"
            + " r.user_id=?", owner);
  }

  public void deleteAttempts(long owner) {
    db.update("DELETE a FROM ai_attempts a JOIN ai_requests r ON r.id=a.ai_request_id WHERE"
            + " r.user_id=?", owner);
  }

  public void deleteRequests(long owner) {
    db.update("DELETE FROM ai_requests WHERE user_id=?", owner);
  }
}
