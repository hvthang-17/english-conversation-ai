package vn.englishconversation.ai.repository;

import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;

/**
 * Request/attempt persistence only. Quota reads and claims must run inside admission reservation.
 * Completion/expiration writes require the caller's session lock and attempt/version checks.
 * This repository does not start transactions or decide whether a retry is allowed.
 */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class AiWorkRepository {
  private final JdbcStore db;
  public AiWorkRepository(JdbcStore db) { this.db = db; }

  public record SelectedOperation(
      long id,
      String taskType,
      String status,
      String errorCode,
      boolean retryable,
      long attemptCount,
      String retryIso
  ) {}
  public record ExpiredRequest(long id,long attemptCount,String taskType) {}
  public record RequestState(
      long id,
      String taskType,
      String status,
      String errorCode,
      boolean retryable,
      long attemptCount,
      String promptVersion
  ) {}

  public long retryDelayCount(long operation) {
    return db.count("SELECT COUNT(*) FROM ai_requests WHERE id=? AND retry_not_before>UTC_TIMESTAMP(6)", operation);
  }

  public void releaseUncertain(long operation) {
    db.update("UPDATE ai_requests SET status='FAILED',retryable=TRUE WHERE id=?", operation);
  }

  public void skipRequest(long operation) {
    db.update("UPDATE ai_requests SET status='FAILED',retryable=FALSE,error_code='SKIPPED' WHERE"
                  + " id=?", operation);
  }

  public long pendingSessionRequests(long session) {
    return db.count("SELECT COUNT(*) FROM ai_requests WHERE session_id=? AND task_type<>'PRONUNCIATION' AND status IN"
                        + " ('PENDING','RUNNING')", session);
  }


  public void closeUncertainChat(long session) {
    db.update("""
UPDATE ai_requests SET status='FAILED',retryable=FALSE,error_code='PRACTICE_CLOSED_UNKNOWN'
WHERE session_id=? AND task_type IN ('CHAT','HINT') AND status='UNKNOWN'
""", session);
  }

  public long globalMinuteAttempts() {
    return db.count("SELECT COUNT(*) FROM ai_attempts WHERE provider='GEMINI' AND started_at>=UTC_TIMESTAMP(6)-INTERVAL 60"
                    + " SECOND");
  }

  public long userMinuteAttempts(long owner) {
    return db.count("""
                SELECT COUNT(*) FROM ai_attempts a JOIN ai_requests r ON r.id=a.ai_request_id
                WHERE r.user_id=? AND a.provider='GEMINI' AND a.started_at>=UTC_TIMESTAMP(6)-INTERVAL 60 SECOND
                """, owner);
  }

  public long runningRequests() {
    return db.count("SELECT COUNT(*) FROM ai_requests WHERE status='RUNNING' AND task_type<>'PRONUNCIATION' AND"
                    + " processing_expires_at>UTC_TIMESTAMP(6)");
  }

  public long totalAttempts() {
    return db.count("SELECT COUNT(*) FROM ai_attempts WHERE provider='GEMINI'");
  }

  public void claimRequest(int attempt,int lease,String feedbackVersion,long operation) {
    db.update("""
UPDATE ai_requests SET status='RUNNING',attempt_count=?,retryable=FALSE,error_code=NULL,finished_at=NULL,
retry_not_before=NULL,processing_expires_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),
prompt_version=IF(task_type='FEEDBACK',?,prompt_version) WHERE id=?
""", attempt, lease, feedbackVersion, operation);
  }

  public long startAttempt(long operation,int attempt,String model) {
    return db.insert("""
        INSERT INTO ai_attempts(ai_request_id,attempt_no,model_name,status,started_at)
        VALUES (?,?,?,'RUNNING',UTC_TIMESTAMP(6))
        """, operation, attempt, model);
  }

  public void completeAttempt(
      String status,
      String model,
      String providerId,
      Long inputTokens,
      Long outputTokens,
      long latency,
      java.math.BigDecimal cost,
      String pricingReference,
      String errorCode,
      long operation,
      int attempt
  ) {
    db.update("""
UPDATE ai_attempts SET status=?,model_name=COALESCE(?,model_name),provider_request_id=?,
input_tokens=?,output_tokens=?,latency_ms=?,estimated_cost=?,pricing_reference=?,error_code=?,
finished_at=UTC_TIMESTAMP(6) WHERE ai_request_id=? AND attempt_no=?
""", status, model, providerId, inputTokens, outputTokens, latency, cost, pricingReference, errorCode, operation, attempt);
  }

  public void completeRequest(
      String status,
      boolean retryable,
      String errorCode,
      int retrySeconds,
      long operation
  ) {
    db.update("""
UPDATE ai_requests SET status=?,retryable=?,error_code=?,finished_at=UTC_TIMESTAMP(6),processing_expires_at=NULL,
retry_not_before=CASE WHEN ?>0 THEN TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)) ELSE NULL END WHERE id=?
""", status, retryable, errorCode, retrySeconds, retrySeconds, operation);
  }

  public java.util.Optional<ExpiredRequest> expiredRequest(long session) {
    return db.find("""
            SELECT id,attempt_count,task_type FROM ai_requests WHERE busy_session_id=? AND status='RUNNING'
            AND processing_expires_at<=UTC_TIMESTAMP(6)
            """, session).map(row -> new ExpiredRequest(row.number("id"), row.number("attempt_count"), row.text("task_type")));
  }

  public void expireAttempt(long operation,long attempt) {
    db.update("""
UPDATE ai_attempts SET status='UNKNOWN',error_code='LEASE_EXPIRED',finished_at=UTC_TIMESTAMP(6)
WHERE ai_request_id=? AND attempt_no=? AND status='RUNNING'
""", operation, attempt);
  }

  public void expireAuxiliaryRequest(long operation) {
    db.update("""
          UPDATE ai_requests SET status='FAILED',retryable=TRUE,error_code='LEASE_EXPIRED',
            finished_at=UTC_TIMESTAMP(6),processing_expires_at=NULL WHERE id=?
          """, operation);
  }

  public void expireRequest(long operation) {
    db.update("""
UPDATE ai_requests SET status='UNKNOWN',error_code='LEASE_EXPIRED',finished_at=UTC_TIMESTAMP(6),
processing_expires_at=NULL WHERE id=?
""", operation);
  }

  public java.util.Optional<SelectedOperation> selectedOperation(
      long session,
      Long preferred,
      Long preferredOrder
  ) {
    return db.find("""
SELECT r.*, DATE_FORMAT(r.retry_not_before,'%Y-%m-%dT%H:%i:%sZ') AS retry_iso
FROM ai_requests r WHERE r.session_id=? AND r.task_type NOT IN ('TRANSCRIPTION','EXPLAIN','PRONUNCIATION') AND (r.busy_session_id IS NOT NULL OR r.id=?
OR r.status='FAILED' AND r.task_type<>'HINT' AND COALESCE(r.error_code,'')<>'SKIPPED' AND (
  r.id=(SELECT t.ai_request_id FROM conversation_turns t WHERE t.session_id=r.session_id ORDER BY t.turn_no DESC LIMIT 1)
  OR r.id=(SELECT MAX(latest.id) FROM ai_requests latest WHERE latest.session_id=r.session_id AND latest.task_type NOT IN ('TRANSCRIPTION','EXPLAIN','PRONUNCIATION'))))
ORDER BY CASE WHEN r.busy_session_id IS NOT NULL THEN 0
  WHEN r.task_type='CHAT' AND r.status='FAILED' THEN 1 WHEN r.id=? THEN 2 ELSE 3 END,r.id DESC LIMIT 1
""", session, preferred, preferredOrder).map(row -> new SelectedOperation(row.number("id"), row.text("task_type"), row.text("status"), row.text("error_code"), row.flag("retryable"), row.number("attempt_count"), row.text("retry_iso")));
  }

  public java.util.Optional<RequestState> request(long session,long operation) {
    return db.find("SELECT * FROM ai_requests WHERE session_id=? AND id=?", session, operation).map(row -> new RequestState(row.number("id"), row.text("task_type"), row.text("status"), row.text("error_code"), row.flag("retryable"), row.number("attempt_count"), row.text("prompt_version")));
  }

  public long createRequest(
      long owner,
      long session,
      String task,
      String operationKey,
      String fingerprint,
      String promptVersion
  ) {
    return db.insert("""
INSERT INTO ai_requests(user_id,session_id,task_type,operation_key,request_fingerprint,status,prompt_version,created_at)
VALUES (?,?,?,?,?,'PENDING',?,UTC_TIMESTAMP(6))
""", owner, session, task, operationKey, fingerprint, promptVersion);
  }
}
