package vn.englishconversation.conversation.repository;

import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;

/**
 * Caller locks the owner/session before using help state or changing it. Mutations participate
 * in that transaction; no independent transaction or provider call is started here.
 * HintLink.messageId is nullable because priorHint uses a LEFT JOIN.
 */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class AssistantHelpRepository {
  private final JdbcStore db;
  public AssistantHelpRepository(JdbcStore db) { this.db = db; }

  public record ExplanationState(
      boolean unknownAttempt,
      String status,
      String errorCode,
      boolean retryable,
      long attemptCount,
      String retryIso,
      String simpleEnglish,
      String meaningVi
  ) {}
  public record HelpMessage(long messageId) {}
  public record HelpOperation(long aiRequestId) {}
  public record HintLink(long id,Long messageId) {}
  public record HintState(
      long aiRequestId,
      boolean unknownAttempt,
      String status,
      String hintText,
      String errorCode,
      boolean retryable,
      long attemptCount,
      String retryIso
  ) {}

  public java.util.Optional<HintLink> priorHint(long session,String key) {
    return db.find("""
          SELECT r.id,h.message_id FROM ai_requests r LEFT JOIN assistant_hints h ON h.ai_request_id=r.id
          WHERE r.session_id=? AND r.task_type='HINT' AND r.operation_key=?
          """, session, key).map(row -> new HintLink(row.number("id"), (row.data().get("message_id") == null ? null : ((Number) row.data().get("message_id")).longValue())));
  }

  public java.util.Optional<HelpOperation> hintOperation(long session,long message) {
    return db.find("SELECT ai_request_id FROM assistant_hints WHERE session_id=? AND message_id=?", session, message).map(row -> new HelpOperation(row.number("ai_request_id")));
  }

  public void createHint(long session,long message,long operation) {
    db.update("INSERT INTO assistant_hints(session_id,message_id,ai_request_id) VALUES (?,?,?)", session, message, operation);
  }

  public java.util.Optional<HintState> hintState(long session,long message) {
    return db.find("""
        SELECT h.*,r.status,r.error_code,r.retryable,r.attempt_count,
          COALESCE(a.status='UNKNOWN',FALSE) AS unknown_attempt,
          DATE_FORMAT(r.retry_not_before,'%Y-%m-%dT%H:%i:%sZ') AS retry_iso
        FROM assistant_hints h JOIN ai_requests r ON r.id=h.ai_request_id
        LEFT JOIN ai_attempts a ON a.ai_request_id=r.id AND a.attempt_no=r.attempt_count
        WHERE h.session_id=? AND h.message_id=?
        """, session, message).map(row -> new HintState(row.number("ai_request_id"), row.flag("unknown_attempt"), row.text("status"), row.text("hint_text"), row.text("error_code"), row.flag("retryable"), row.number("attempt_count"), row.text("retry_iso")));
  }

  public java.util.Optional<HelpMessage> hintMessage(long operation) {
    return db.find("SELECT message_id FROM assistant_hints WHERE ai_request_id=?", operation).map(row -> new HelpMessage(row.number("message_id")));
  }

  public long conflictingExplanationCount(long session,String key,long message) {
    return db.count("""
          SELECT COUNT(*) FROM ai_requests r JOIN assistant_explanations e ON e.ai_request_id=r.id
          WHERE r.session_id=? AND r.task_type='EXPLAIN' AND r.operation_key=? AND e.message_id<>?
          """, session, key, message);
  }

  public java.util.Optional<HelpOperation> explanationOperation(long session,long message) {
    return db.find("SELECT ai_request_id FROM assistant_explanations WHERE session_id=? AND message_id=?", session, message).map(row -> new HelpOperation(row.number("ai_request_id")));
  }

  public void createExplanation(long session,long message,long operation) {
    db.update("INSERT INTO assistant_explanations(session_id,message_id,ai_request_id) VALUES (?,?,?)", session, message, operation);
  }

  public void saveExplanation(String simpleEnglish,String meaningVi,long operation) {
    db.update("UPDATE assistant_explanations SET simple_english=?,meaning_vi=? WHERE ai_request_id=?", simpleEnglish, meaningVi, operation);
  }

  public java.util.Optional<ExplanationState> explanationState(long session,long message) {
    return db.find("""
        SELECT e.*,r.status,r.error_code,r.retryable,r.attempt_count,
          COALESCE(a.status='UNKNOWN',FALSE) AS unknown_attempt,
          DATE_FORMAT(r.retry_not_before,'%Y-%m-%dT%H:%i:%sZ') AS retry_iso
        FROM assistant_explanations e JOIN ai_requests r ON r.id=e.ai_request_id
        LEFT JOIN ai_attempts a ON a.ai_request_id=r.id AND a.attempt_no=r.attempt_count
        WHERE e.session_id=? AND e.message_id=?
        """, session, message).map(row -> new ExplanationState(row.flag("unknown_attempt"), row.text("status"), row.text("error_code"), row.flag("retryable"), row.number("attempt_count"), row.text("retry_iso"), row.text("simple_english"), row.text("meaning_vi")));
  }

  public void saveHint(String hint,long operation) {
    db.update("UPDATE assistant_hints SET hint_text=? WHERE ai_request_id=?", hint, operation);
  }
}
