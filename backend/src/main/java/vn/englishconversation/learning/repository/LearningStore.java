package vn.englishconversation.learning.repository;

import static vn.englishconversation.learning.dto.LearningData.*;

import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;

@Repository
@Profile("g6 | (!bootstrap & !g1 & !g2 & !g3 & !g4 & !g5)")
public class LearningStore {
  private static final String CARD_FIELDS =
      """
SELECT c.*, DATE_FORMAT(last_reviewed_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS reviewed_iso FROM review_items c
""";
  private final JdbcStore db;

  public LearningStore(JdbcStore db) {
    this.db = db;
  }

  public boolean lockAccount(long user, long version) {
    return db.find(
            "SELECT id FROM users WHERE id=? AND status='ACTIVE' AND deletion_requested_at IS NULL AND security_version=? FOR UPDATE",
            user,
            version)
        .isPresent();
  }

  public Optional<Row> suggestion(long user, long id) {
    return db.find(
        """
        SELECT s.item_type,s.expression,s.meaning_vi,s.example_en,s.part_of_speech
        FROM feedback_suggestions s JOIN session_feedback f ON f.id=s.feedback_id
        JOIN practice_sessions p ON p.id=f.session_id
        WHERE s.id=? AND p.user_id=? AND f.status='SUCCEEDED'
        """,
        id,
        user);
  }

  public Card saveCard(long user, long suggestion, Row source) {
    var prior =
        db.find(
            "SELECT id FROM review_items WHERE user_id=? AND source_suggestion_id=?",
            user,
            suggestion);
    if (prior.isPresent()) return card(user, prior.get().number("id"));
    long id =
        db.insert(
            """
INSERT INTO review_items(user_id,source_suggestion_id,item_type,expression,meaning_vi,example_en,
  part_of_speech,status,created_at,updated_at)
VALUES (?,?,?,?,?,?,?,'NEEDS_REVIEW',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
""",
            user,
            suggestion,
            source.text("item_type"),
            source.text("expression"),
            source.text("meaning_vi"),
            source.text("example_en"),
            source.text("part_of_speech"));
    return card(user, id);
  }

  public boolean hasCard(long user, long id) {
    return db.find("SELECT id FROM review_items WHERE id=? AND user_id=? FOR UPDATE", id, user)
        .isPresent();
  }

  public Optional<Row> priorAnswer(long id, String key) {
    return db.find(
        "SELECT result FROM review_attempts WHERE review_item_id=? AND client_request_id=?",
        id,
        key);
  }

  public void recordAnswer(long user, long id, ReviewAnswer answer) {
    db.insert(
        """
INSERT INTO review_attempts(review_item_id,review_mode,result,client_request_id,reviewed_at)
VALUES (?,'FLASHCARD',?,?,UTC_TIMESTAMP(6))
""",
        id,
        answer.result(),
        answer.clientRequestId());
    db.update(
        """
UPDATE review_items SET status=?,last_reviewed_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6)
WHERE id=? AND user_id=?
""",
        answer.result(),
        id,
        user);
  }

  public Card card(long user, long id) {
    return db.find(CARD_FIELDS + " WHERE c.user_id=? AND c.id=?", user, id)
        .map(LearningStore::toCard)
        .orElseThrow();
  }

  public Page<Card> cards(long user, String status, int page, int size) {
    String filter = " WHERE user_id=?" + (status == null ? "" : " AND status=?");
    Object[] countArgs = status == null ? new Object[] {user} : new Object[] {user, status};
    Object[] pageArgs =
        status == null
            ? new Object[] {user, size, (long) page * size}
            : new Object[] {user, status, size, (long) page * size};
    long total = db.count("SELECT COUNT(*) FROM review_items" + filter, countArgs);
    var items =
        db.rows(CARD_FIELDS + filter + " ORDER BY id DESC LIMIT ? OFFSET ?", pageArgs).stream()
            .map(LearningStore::toCard)
            .toList();
    return new Page<>(items, page, size, total);
  }

  private static Card toCard(Row row) {
    return new Card(
        row.text("id"),
        row.text("source_suggestion_id"),
        row.text("item_type"),
        row.text("expression"),
        row.text("meaning_vi"),
        row.text("example_en"),
        row.text("status"),
        row.text("reviewed_iso"),
        row.text("part_of_speech"));
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

  public void deletePlans(long owner) {
    db.update("DELETE FROM learning_plans WHERE user_id=?", owner);
  }

  public void deleteCards(long user) {
    // FK cascades review_attempts, within the caller's transaction.
    db.update("DELETE FROM review_items WHERE user_id=?", user);
  }

  public Statistics statistics(long user) {
    long completed =
        db.count(
            "SELECT COUNT(*) FROM practice_sessions WHERE user_id=? AND status='COMPLETED'", user);
    Row activity =
        db.find(
                """
SELECT COUNT(*) AS turns,COUNT(DISTINCT DATE(m.created_at+INTERVAL 7 HOUR)) AS days
FROM messages m JOIN practice_sessions p ON p.id=m.session_id WHERE p.user_id=? AND m.sender='LEARNER'
""",
                user)
            .orElseThrow();
    var days =
        db
            .rows(
                """
SELECT DATE_FORMAT(m.created_at+INTERVAL 7 HOUR,'%Y-%m-%d') AS label,COUNT(*) AS total
FROM messages m JOIN practice_sessions p ON p.id=m.session_id WHERE p.user_id=? AND m.sender='LEARNER'
GROUP BY label ORDER BY label DESC LIMIT 30
""",
                user)
            .stream()
            .map(LearningStore::group)
            .toList();
    var topics =
        db
            .rows(
                """
SELECT t.name AS topic,SUM(p.status='COMPLETED') AS completed,SUM(
    (SELECT COUNT(*) FROM messages m WHERE m.session_id=p.id AND m.sender='LEARNER')) AS turns
FROM practice_sessions p JOIN scenarios s ON s.id=p.scenario_id JOIN topics t ON t.id=s.topic_id
WHERE p.user_id=? GROUP BY t.id,t.name ORDER BY completed DESC,turns DESC,t.name,t.id
""",
                user)
            .stream()
            .map(t -> new TopicActivity(t.text("topic"), t.number("completed"), t.number("turns")))
            .toList();
    var errors =
        db
            .rows(
                """
                SELECT i.category AS label,COUNT(DISTINCT i.source_message_id) AS total
                FROM feedback_items i JOIN session_feedback f ON f.id=i.feedback_id
                JOIN practice_sessions p ON p.id=i.session_id
                WHERE p.user_id=? AND f.status='SUCCEEDED' AND i.item_type='ERROR'
                GROUP BY label ORDER BY total DESC,label
                """,
                user)
            .stream()
            .map(LearningStore::group)
            .toList();
    return new Statistics(
        "Asia/Ho_Chi_Minh",
        completed,
        activity.number("turns"),
        activity.number("days"),
        days,
        topics,
        errors,
        recommendations(user));
  }

  private static Group group(Row row) {
    return new Group(row.text("label"), row.number("total"));
  }

  private List<Recommendation> recommendations(long user) {
    return db
        .rows(
            """
            SELECT s.id,s.title,l.level,(i.topic_id IS NOT NULL) AS preferred,
              (SELECT COUNT(*) FROM practice_sessions p WHERE p.user_id=u.id
                AND p.scenario_id=s.id AND p.status='COMPLETED') AS completed
            FROM users u LEFT JOIN learner_profiles profile ON profile.user_id=u.id
            JOIN scenario_levels l ON l.level=COALESCE(profile.selected_level,'A2')
            JOIN scenarios s ON s.id=l.scenario_id AND s.status='ACTIVE'
            JOIN topics t ON t.id=s.topic_id AND t.is_active=TRUE
            LEFT JOIN user_topic_interests i ON i.user_id=u.id AND i.topic_id=t.id
            WHERE u.id=? ORDER BY preferred DESC,completed ASC,t.sort_order,s.id LIMIT 5
            """,
            user)
        .stream()
        .map(
            s ->
                new Recommendation(
                    s.text("id"),
                    s.text("title"),
                    s.text("level"),
                    s.flag("preferred")
                        ? "Chủ đề bạn quan tâm"
                        : s.number("completed") == 0
                            ? "Bài chưa hoàn thành ở trình độ của bạn"
                            : "Luyện lại ở trình độ của bạn"))
        .toList();
  }
}
