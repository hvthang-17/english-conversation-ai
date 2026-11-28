package vn.englishconversation.pronunciation.repository;

import org.springframework.stereotype.Repository;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.englishconversation.common.repository.JdbcStore;

/** Persistence only: caller retains transaction, lock ordering, claim/version and cleanup policy. */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class ConversationPronunciationRepository {
  private final JdbcStore db;
  private final JdbcTemplate jdbc;
  public ConversationPronunciationRepository(JdbcStore db, JdbcTemplate jdbc) { this.db = db; this.jdbc = jdbc; }
  public record RawAudio(String storageKey,long durationMs,boolean expired,String requestFingerprint) {}
  public record Preparation(long id,long sessionId,long userId) {}
  public record ScoreRow(long id,String status,String errorCode,java.math.BigDecimal overallScore,java.math.BigDecimal accuracy,java.math.BigDecimal fluency,String recognizedText,String detailJson) {}
  public java.util.Optional<RawAudio> lockConfirmedAudio(long audio,long session) {
    return db.find("""
        SELECT storage_key,duration_ms,expires_at<=UTC_TIMESTAMP(6) AS expired FROM audio_submissions
        WHERE id=? AND session_id=? AND status='CONFIRMED' FOR UPDATE
        """, audio, session).map(row -> new RawAudio(row.text("storage_key"), row.number("duration_ms"), row.flag("expired"), null));
  }

  public java.util.List<Preparation> pendingPreparations() {
    return db.rows("""
        SELECT a.id,a.session_id,s.user_id FROM audio_submissions a JOIN practice_sessions s ON s.id=a.session_id
        WHERE a.assessment_status='PENDING_PREPARATION' AND a.status='CONFIRMED'
        ORDER BY a.id LIMIT 10
        """).stream().map(row -> new Preparation(row.number("id"), row.number("session_id"), row.number("user_id"))).toList();
  }

  public void failPreparation(long audio,long session) {
    db.update("""
            UPDATE audio_submissions SET assessment_status='FAILED',assessment_error_code='PREPARATION_FAILED'
            WHERE id=? AND session_id=? AND status='CONFIRMED' AND assessment_status='PENDING_PREPARATION'
            """, audio, session);
  }

  public void skipPreparation(long audio,long session) {
    db.update("""
          UPDATE audio_submissions SET assessment_status='SKIPPED',assessment_error_code='ASSESSMENT_UNAVAILABLE'
          WHERE id=? AND session_id=? AND status='CONFIRMED' AND assessment_status='PENDING_PREPARATION'
          """, audio, session);
  }

  public java.util.Optional<RawAudio> lockPendingAudio(long audio,long session) {
    return db.find("""
        SELECT storage_key,request_fingerprint,duration_ms,expires_at<=UTC_TIMESTAMP(6) AS expired
        FROM audio_submissions WHERE id=? AND session_id=? AND status='CONFIRMED'
          AND assessment_status='PENDING_PREPARATION' FOR UPDATE
        """, audio, session).map(row -> new RawAudio(row.text("storage_key"), row.number("duration_ms"), row.flag("expired"), row.text("request_fingerprint")));
  }

  public long createRequest(long owner,long session,String operation,String fingerprint) {
    return db.insert("""
        INSERT INTO ai_requests(user_id,session_id,task_type,operation_key,request_fingerprint,prompt_version,created_at)
        VALUES (?,?,'PRONUNCIATION',?,?,'azure-en-us-unscripted-v1',UTC_TIMESTAMP(6))
        """, owner, session, operation, fingerprint);
  }

  public long createAttempt(long owner,long request,String operation,String key,long audio) {
    return db.insert("""
        INSERT INTO pronunciation_attempts(user_id,ai_request_id,client_request_id,mode,status,
          provider,locale,assessment_version,audio_submission_id,audio_storage_key,audio_expires_at,created_at)
        SELECT ?,?,?,'UNSCRIPTED','PENDING','AZURE_SPEECH','en-US','azure-en-us-unscripted-v1',id,?,
          expires_at,UTC_TIMESTAMP(6) FROM audio_submissions WHERE id=?
        """, owner, request, operation, key, audio);
  }

  public void setPreparation(String status,String reason,long audio) {
    db.update("UPDATE audio_submissions SET assessment_status=?,assessment_error_code=? WHERE id=?", status, reason, audio);
  }

  public java.util.List<ScoreRow> scores(long session) {
    return db.rows("""
        SELECT m.id,COALESCE(p.status,a.assessment_status,'NOT_RECORDED') AS status,
          CASE WHEN p.status='FAILED' AND p.error_code='PROCESSING_EXPIRED' THEN 'AUDIO_EXPIRED'
            ELSE COALESCE(p.error_code,a.assessment_error_code) END AS error_code,p.overall_score,p.accuracy,p.fluency,
          p.recognized_text,p.detail_json FROM messages m
        JOIN audio_submissions a ON a.id=m.audio_submission_id AND a.session_id=m.session_id
        LEFT JOIN pronunciation_attempts p ON p.audio_submission_id=a.id WHERE m.session_id=?
        """, session).stream().map(row -> new ScoreRow(row.number("id"), row.text("status"), row.text("error_code"), (java.math.BigDecimal) row.data().get("overall_score"), (java.math.BigDecimal) row.data().get("accuracy"), (java.math.BigDecimal) row.data().get("fluency"), row.text("recognized_text"), row.text("detail_json"))).toList();
  }
}
