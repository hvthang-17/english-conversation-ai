package vn.englishconversation.listening.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;

@Repository
@Profile({"g4", "g5", "g6"})
public class ListeningRepository {
  private static final String EXERCISE_SOURCE = """
      FROM listening_exercises e
      JOIN lessons l ON l.id=e.lesson_id JOIN topics t ON t.id=l.topic_id
      """;
  private static final String EXERCISES = "SELECT e.*,l.level " + EXERCISE_SOURCE;
  private static final String CATALOG = "SELECT e.id,e.title,e.instructions,l.level " + EXERCISE_SOURCE;
  private static final String PUBLIC_EXERCISES =
      " e.status='ACTIVE' AND l.status='ACTIVE' AND t.is_active=TRUE";
  private static final String ATTEMPTS = """
      SELECT a.*,e.title,e.instructions,l.level,
             DATE_FORMAT(a.started_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS started_utc
      FROM listening_attempts a JOIN listening_exercises e ON e.id=a.exercise_id
      JOIN lessons l ON l.id=e.lesson_id
      """;
  private final JdbcStore db;

  public ListeningRepository(JdbcStore db) { this.db = db; }

  public List<Row> available(long lessonId) {
    return db.rows(CATALOG + " WHERE e.lesson_id=? AND" + PUBLIC_EXERCISES
        + " ORDER BY e.sort_order,e.id", lessonId);
  }

  public List<Row> available(int page, int size) {
    return db.rows(CATALOG + " WHERE" + PUBLIC_EXERCISES
        + " ORDER BY e.lesson_id,e.sort_order,e.id LIMIT ? OFFSET ?", size, (long) page * size);
  }

  public long availableCount() {
    return db.count("SELECT COUNT(*) " + EXERCISE_SOURCE + " WHERE" + PUBLIC_EXERCISES);
  }

  public Optional<Row> exercise(long id) {
    return db.find(EXERCISES + " WHERE e.id=?", id);
  }

  public Optional<Row> lockExercise(long id) {
    return db.find("SELECT * FROM listening_exercises WHERE id=? FOR UPDATE", id);
  }

  public void lockLesson(long id) {
    db.find("SELECT id FROM lessons WHERE id=? FOR UPDATE", id);
  }

  public boolean publicLesson(long id, boolean lock) {
    return db.find("""
        SELECT l.id FROM lessons l JOIN topics t ON t.id=l.topic_id
        WHERE l.id=? AND l.status='ACTIVE' AND t.is_active=TRUE
        """ + (lock ? " FOR SHARE" : ""), id).isPresent();
  }

  public boolean visible(long id) {
    return db.find(EXERCISES + " WHERE e.id=? AND" + PUBLIC_EXERCISES + " FOR SHARE",
        id).isPresent();
  }

  public List<Row> versions() {
    return db.rows(EXERCISES + " ORDER BY l.id,e.id DESC");
  }

  public void activate(long lessonId, long exerciseId) {
    db.update("""
        UPDATE listening_exercises SET status='ARCHIVED',updated_at=UTC_TIMESTAMP(6)
        WHERE lesson_id=? AND id<>? AND status='ACTIVE'
        """, lessonId, exerciseId);
    db.update("UPDATE listening_exercises SET status='ACTIVE',updated_at=UTC_TIMESTAMP(6) WHERE id=?", exerciseId);
  }

  public Optional<Row> lockAttempt(long owner, long id) {
    return db.find("SELECT * FROM listening_attempts WHERE user_id=? AND id=? FOR UPDATE", owner, id);
  }

  public Optional<Row> attempt(long owner, long id) {
    return db.find(ATTEMPTS + " WHERE a.user_id=? AND a.id=?", owner, id);
  }

  public Optional<Row> byKey(long owner, String key) {
    return db.find("SELECT id,exercise_id FROM listening_attempts WHERE user_id=? AND client_request_id=?", owner, key);
  }

  public Optional<Row> inProgress(long owner, long exercise) {
    return db.find("SELECT id FROM listening_attempts WHERE user_id=? AND exercise_id=? AND status='IN_PROGRESS'",
        owner, exercise);
  }

  public long begin(long owner, long exercise, String key, int maximum) {
    return db.insert("""
        INSERT INTO listening_attempts
          (user_id,exercise_id,client_request_id,status,max_score,answers_json,started_at)
        VALUES (?,?,?,'IN_PROGRESS',?,JSON_OBJECT(),UTC_TIMESTAMP(6))
        """, owner, exercise, key, maximum);
  }

  public void draft(long id, String answers) {
    db.update("UPDATE listening_attempts SET answers_json=?,draft_version=draft_version+1 WHERE id=?", answers, id);
  }

  public void submit(long id, int score, String details) {
    db.update("""
        UPDATE listening_attempts SET status='SUBMITTED',score=?,answers_json=?,submitted_at=UTC_TIMESTAMP(6) WHERE id=?
        """, score, details, id);
  }

  public void abandon(long id) {
    db.update("UPDATE listening_attempts SET status='ABANDONED' WHERE id=?", id);
  }

  public long historyCount(long owner) {
    return db.count("SELECT COUNT(*) FROM listening_attempts WHERE user_id=?", owner);
  }

  public List<Row> history(long owner, int size, int page) {
    return db.rows(ATTEMPTS + " WHERE a.user_id=? ORDER BY a.started_at DESC,a.id DESC LIMIT ? OFFSET ?",
        owner, size, (long) size * page);
  }

  public void clear(long owner) {
    db.update("DELETE FROM listening_attempts WHERE user_id=?", owner);
  }
}
