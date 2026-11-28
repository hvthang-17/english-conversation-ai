package vn.englishconversation.conversation.repository;

import static vn.englishconversation.learning.dto.LearningData.*;

import java.util.List;
import java.util.Optional;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class ConversationHistoryRepository {
  private final JdbcStore db;

  public ConversationHistoryRepository(JdbcStore db) {
    this.db = db;
  }

  public Page<HistoryEntry> history(long user, int page, int size) {
    long total = db.count("SELECT COUNT(*) FROM practice_sessions WHERE user_id=?", user);
    var rows =
        db.rows(
            """
SELECT p.id,JSON_UNQUOTE(JSON_EXTRACT(p.scenario_snapshot,'$.title')) AS title,
  p.selected_level,p.status,COALESCE(f.status,'NOT_STARTED') AS feedback_status,
  DATE_FORMAT(p.started_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS started_iso,
  DATE_FORMAT(p.ended_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS ended_iso,
  (SELECT COUNT(*) FROM messages m WHERE m.session_id=p.id AND m.sender='LEARNER') AS turns
FROM practice_sessions p LEFT JOIN session_feedback f ON f.session_id=p.id
WHERE p.user_id=? ORDER BY p.started_at DESC,p.id DESC LIMIT ? OFFSET ?
""",
            user, size, (long) page * size);
    var items =
        rows.stream()
            .map(
                p ->
                    new HistoryEntry(
                        p.text("id"),
                        p.text("title"),
                        p.text("selected_level"),
                        p.text("status"),
                        p.text("started_iso"),
                        p.text("ended_iso"),
                        p.number("turns"),
                        p.text("feedback_status")))
            .toList();
    return new Page<>(items, page, size, total);
  }

  public boolean lockSession(long user, long id) {
    return db.find("SELECT id FROM practice_sessions WHERE user_id=? AND id=? FOR UPDATE", user, id)
        .isPresent();
  }

  public List<Long> lockAllSessions(long user) {
    return db
        .rows("SELECT id FROM practice_sessions WHERE user_id=? ORDER BY id FOR UPDATE", user)
        .stream()
        .map(row -> row.number("id"))
        .toList();
  }

  public void deletePractice(long user, long id) {
    db.update("""
        UPDATE support_tickets t
        LEFT JOIN messages m ON m.id=t.message_id
        LEFT JOIN session_feedback f ON f.id=t.feedback_id
        SET t.message_id=NULL,t.feedback_id=NULL,t.redacted_excerpt=NULL,t.source_removed_at=UTC_TIMESTAMP(6)
        WHERE t.user_id=? AND (m.session_id=? OR f.session_id=?)
        """, user, id, id);
    db.update("DELETE FROM feedback_items WHERE session_id=?", id);
    // Deleting suggestions detaches references while preserving the saved card copies.
    db.update(
        """
DELETE s FROM feedback_suggestions s JOIN session_feedback f ON f.id=s.feedback_id WHERE f.session_id=?
""",
        id);
    db.update("DELETE FROM session_feedback WHERE session_id=?", id);
    db.update("DELETE FROM messages WHERE session_id=?", id);
    db.update("""
        UPDATE pronunciation_attempts p JOIN ai_requests r ON r.id=p.ai_request_id
        SET p.status='FAILED',p.error_code='PRACTICE_DELETED',p.recognized_text=NULL,
          p.overall_score=NULL,p.accuracy=NULL,p.fluency=NULL,p.detail_json=NULL,p.scored_at=NULL
        WHERE r.session_id=? AND r.task_type='PRONUNCIATION'
        """, id);
    db.update(
        """
        UPDATE audio_submissions SET status='DELETION_PENDING',draft_transcript=NULL,
          transcript_expires_at=NULL,session_id=NULL WHERE session_id=?
        """, id);
    db.update("DELETE FROM conversation_turns WHERE session_id=?", id);
    // Provider completion must still acquire the same session lock before persisting.
    db.update(
        """
        UPDATE ai_attempts a JOIN ai_requests r ON r.id=a.ai_request_id
        SET a.status='UNKNOWN', a.error_code='PRACTICE_DELETED', a.finished_at=UTC_TIMESTAMP(6)
        WHERE r.user_id=? AND r.session_id=? AND a.status='RUNNING'
        """,
        user,
        id);
    db.update(
        """
        UPDATE ai_requests SET
          error_code=IF(status IN ('PENDING','RUNNING'),'PRACTICE_DELETED',error_code),
          finished_at=IF(status IN ('PENDING','RUNNING'),UTC_TIMESTAMP(6),finished_at),
          status=IF(status IN ('PENDING','RUNNING'),'UNKNOWN',status),
          retryable=FALSE, retry_not_before=NULL, processing_expires_at=NULL, session_id=NULL
        WHERE user_id=? AND session_id=?
        """,
        user,
        id);
    db.update("DELETE FROM practice_sessions WHERE user_id=? AND id=?", user, id);
  }

}
