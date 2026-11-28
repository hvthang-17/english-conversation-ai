package vn.englishconversation.voice.repository;

import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;

/**
 * Draft persistence only. Caller must hold the owner/session lock for reads followed by mutation.
 * All writes join the caller's transaction; file operations remain with VoiceStorage.
 */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class VoiceDraftRepository {
  private final JdbcStore db;
  public VoiceDraftRepository(JdbcStore db) { this.db = db; }

  public record AudioLink(long aiRequestId) {}
  public record AudioPrior(long aiRequestId,String requestFingerprint,String status,String storageKey) {}
  public record AudioState(
      long id,
      String status,
      String draftTranscript,
      String draftUntil,
      long durationMs,
      String errorCode,
      boolean retryable,
      boolean canReupload,
      long attemptCount,
      boolean draftExpired,
      boolean unknownOutcome,
      String retryAt
  ) {}

  public java.util.Optional<AudioPrior> priorAudio(long session,String key) {
    return db.find("SELECT * FROM audio_submissions WHERE session_id=? AND client_request_id=?", session, key).map(row -> new AudioPrior(row.number("ai_request_id"), row.text("request_fingerprint"), row.text("status"), row.text("storage_key")));
  }

  public void restartTranscription(String storageKey,long operation) {
    db.update("UPDATE audio_submissions SET status='TRANSCRIBING',storage_key=?,audio_deleted_at=NULL WHERE ai_request_id=?", storageKey, operation);
  }

  public long createAudio(
      long session,
      String key,
      String fingerprint,
      long operation,
      String storageKey,
      String mime,
      int bytes,
      int duration,
      int ttl
  ) {
    return db.insert("""
            INSERT INTO audio_submissions(session_id,client_request_id,request_fingerprint,status,ai_request_id,
              storage_key,mime_type,size_bytes,duration_ms,expires_at,created_at)
            VALUES (?,?,?,'TRANSCRIBING',?,?,?,?,?,TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),UTC_TIMESTAMP(6))
            """, session, key, fingerprint, operation, storageKey, mime, bytes, duration, ttl);
  }

  public void readyTranscript(String transcript,int ttl,long operation) {
    db.update("""
                UPDATE audio_submissions SET status='READY',draft_transcript=?,
                  transcript_expires_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)) WHERE ai_request_id=?
                """, transcript, ttl, operation);
  }

  public java.util.Optional<AudioLink> recoverableAudio(long session) {
    return db.find("""
          SELECT ai_request_id FROM audio_submissions WHERE session_id=? AND (
            (status='TRANSCRIBING' AND expires_at>UTC_TIMESTAMP(6)) OR
            (status='READY' AND transcript_expires_at>UTC_TIMESTAMP(6))
          )
          ORDER BY id DESC LIMIT 1
          """, session).map(row -> new AudioLink(row.number("ai_request_id")));
  }

  public java.util.Optional<AudioLink> audioOperation(long session,long audio) {
    return db.find("SELECT ai_request_id FROM audio_submissions WHERE session_id=? AND id=?", session, audio).map(row -> new AudioLink(row.number("ai_request_id")));
  }

  public java.util.Optional<AudioState> audioState(long session,long operation) {
    return db.find("""
        SELECT a.*, r.error_code, r.retryable, r.attempt_count,
          DATE_FORMAT(a.transcript_expires_at,'%Y-%m-%dT%H:%i:%sZ') AS draft_until,
          DATE_FORMAT(r.retry_not_before,'%Y-%m-%dT%H:%i:%sZ') AS retry_at,
          COALESCE(a.transcript_expires_at<=UTC_TIMESTAMP(6),FALSE) AS draft_expired,
          a.expires_at>UTC_TIMESTAMP(6) AS can_reupload,
          COALESCE(t.status='UNKNOWN',FALSE) AS unknown_outcome
        FROM audio_submissions a JOIN ai_requests r ON r.id=a.ai_request_id
        LEFT JOIN ai_attempts t ON t.ai_request_id=r.id AND t.attempt_no=r.attempt_count
        WHERE a.session_id=? AND a.ai_request_id=?
        """, session, operation).map(row -> new AudioState(row.number("id"), row.text("status"), row.text("draft_transcript"), row.text("draft_until"), row.number("duration_ms"), row.text("error_code"), row.flag("retryable"), row.flag("can_reupload"), row.number("attempt_count"), row.flag("draft_expired"), row.flag("unknown_outcome"), row.text("retry_at")));
  }

  public void failTranscription(long operation) {
    db.update("UPDATE audio_submissions SET status='FAILED' WHERE ai_request_id=? AND status='TRANSCRIBING'", operation);
  }

  public void confirmAudio(long audio) {
    db.update("""
          UPDATE audio_submissions SET status='CONFIRMED',draft_transcript=NULL,transcript_expires_at=NULL,
            confirmed_at=UTC_TIMESTAMP(6) WHERE id=?
          """, audio);
  }

  public void expireAudio(long audio) {
    db.update("""
          UPDATE audio_submissions SET status='EXPIRED',draft_transcript=NULL,transcript_expires_at=NULL
          WHERE id=?
          """, audio);
  }

  public void expireSessionDrafts(long session) {
    db.update("""
                UPDATE audio_submissions SET status='EXPIRED',draft_transcript=NULL,transcript_expires_at=NULL
                WHERE session_id=? AND status IN ('READY','FAILED')
                """, session);
  }
}
