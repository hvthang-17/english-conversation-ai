package vn.englishconversation.voice.repository;

import org.springframework.stereotype.Repository;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.englishconversation.common.repository.JdbcStore;

/** Persistence only: caller retains transaction, lock ordering, claim/version and cleanup policy. */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class VoiceStorageRepository {
  private final JdbcStore db;
  private final JdbcTemplate jdbc;
  public VoiceStorageRepository(JdbcStore db, JdbcTemplate jdbc) { this.db = db; this.jdbc = jdbc; }
  public record AudioId(long id) {}
  public record CleanupAudio(String status,String storageKey,boolean expired,boolean preparing) {}
  public void markFileDeleted(String key) {
    db.update("UPDATE audio_submissions SET storage_key=NULL,audio_deleted_at=UTC_TIMESTAMP(6) WHERE storage_key=?", key);
  }

  public void expireDrafts() {
    db.update("""
        UPDATE audio_submissions SET status='EXPIRED',draft_transcript=NULL,transcript_expires_at=NULL
        WHERE status='READY' AND transcript_expires_at<=UTC_TIMESTAMP(6)
        """);
  }

  public java.util.List<AudioId> cleanupCandidates(boolean conversationScoringSchema) {
    return db.rows("""
        SELECT id FROM audio_submissions WHERE status='DELETION_PENDING'
        OR storage_key IS NOT NULL AND ((status NOT IN ('TRANSCRIBING','READY')
        """ + (conversationScoringSchema ? " AND NOT (assessment_status <=> 'PENDING_PREPARATION')" : "") + """
        ) OR expires_at<=UTC_TIMESTAMP(6))
        ORDER BY id LIMIT 200
        """).stream().map(row -> new AudioId(row.number("id"))).toList();
  }

  public java.util.Optional<CleanupAudio> lockCleanupAudio(boolean conversationScoringSchema, long id) {
    return db.find("""
        SELECT status,storage_key,expires_at<=UTC_TIMESTAMP(6) AS expired,
        """ + (conversationScoringSchema ? "assessment_status <=> 'PENDING_PREPARATION'" : "FALSE") + """
         AS preparing
        FROM audio_submissions WHERE id=? FOR UPDATE
        """, id).map(row -> new CleanupAudio(row.text("status"), row.text("storage_key"), row.flag("expired"), row.flag("preparing")));
  }

  public long scoringCopyCount(long id) {
    return db.count("""
            SELECT COUNT(*) FROM pronunciation_attempts
            WHERE audio_submission_id=? AND audio_storage_key IS NOT NULL
            """, id);
  }

  public void deleteDetachedAudio(long id) {
    db.update("""
        DELETE FROM audio_submissions
        WHERE id=? AND session_id IS NULL AND status='DELETION_PENDING' AND storage_key IS NULL
        """, id);
  }

  public long referenceCount(String key) {
    return db.count("SELECT COUNT(*) FROM audio_submissions WHERE storage_key=?", key);
  }
}
