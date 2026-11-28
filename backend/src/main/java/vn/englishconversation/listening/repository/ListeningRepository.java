package vn.englishconversation.listening.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.LISTENING)
public class ListeningRepository {
  private static final String EXERCISE_SOURCE = """
      FROM listening_exercises e
      JOIN lessons l ON l.id=e.lesson_id JOIN topics t ON t.id=l.topic_id
      """;
  private static final String EXERCISES = """
      SELECT e.*,l.level,DATE_FORMAT(e.updated_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS updated_utc
      """ + EXERCISE_SOURCE;
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

  public List<ExerciseSummary> available(long lessonId) {
    return db.rows(CATALOG + " WHERE e.lesson_id=? AND" + PUBLIC_EXERCISES
        + " ORDER BY e.sort_order,e.id", lessonId).stream().map(ListeningRepository::mapExerciseSummary).toList();
  }

  public List<ExerciseSummary> available(int page, int size) {
    return db.rows(CATALOG + " WHERE" + PUBLIC_EXERCISES
        + " ORDER BY e.lesson_id,e.sort_order,e.id LIMIT ? OFFSET ?", size, (long) page * size).stream().map(ListeningRepository::mapExerciseSummary).toList();
  }

  public long availableCount() {
    return db.count("SELECT COUNT(*) " + EXERCISE_SOURCE + " WHERE" + PUBLIC_EXERCISES);
  }

  public Optional<ExerciseState> exercise(long id) {
    return db.find(EXERCISES + " WHERE e.id=?", id).map(ListeningRepository::mapExerciseState);
  }

  public Optional<ExerciseState> lockExercise(long id) {
    return db.find("""
        SELECT *,DATE_FORMAT(updated_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS updated_utc
        FROM listening_exercises WHERE id=? FOR UPDATE
        """, id).map(ListeningRepository::mapExerciseState);
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

  public List<ExerciseState> versions() {
    return db.rows(EXERCISES + " ORDER BY l.id,e.id DESC").stream().map(ListeningRepository::mapExerciseState).toList();
  }

  public void activate(long lessonId, long exerciseId) {
    db.update("""
        UPDATE listening_exercises SET status='ARCHIVED',updated_at=UTC_TIMESTAMP(6)
        WHERE lesson_id=? AND id<>? AND status='ACTIVE'
        """, lessonId, exerciseId);
    db.update("UPDATE listening_exercises SET status='ACTIVE',updated_at=UTC_TIMESTAMP(6) WHERE id=?", exerciseId);
  }

  public Optional<AttemptState> lockAttempt(long owner, long id) {
    return db.find("SELECT * FROM listening_attempts WHERE user_id=? AND id=? FOR UPDATE", owner, id).map(ListeningRepository::mapAttemptState);
  }

  public Optional<AttemptState> attempt(long owner, long id) {
    return db.find(ATTEMPTS + " WHERE a.user_id=? AND a.id=?", owner, id).map(ListeningRepository::mapAttemptState);
  }

  public Optional<RequestAttempt> byKey(long owner, String key) {
    return db.find("SELECT id,exercise_id FROM listening_attempts WHERE user_id=? AND client_request_id=?", owner, key).map(ListeningRepository::mapRequestAttempt);
  }

  public Optional<AttemptId> inProgress(long owner, long exercise) {
    return db.find("SELECT id FROM listening_attempts WHERE user_id=? AND exercise_id=? AND status='IN_PROGRESS'",
        owner, exercise).map(ListeningRepository::mapAttemptId);
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

  public List<AttemptState> history(long owner, int size, int page) {
    return db.rows(ATTEMPTS + " WHERE a.user_id=? ORDER BY a.started_at DESC,a.id DESC LIMIT ? OFFSET ?",
        owner, size, (long) size * page).stream().map(ListeningRepository::mapAttemptState).toList();
  }

  public void clear(long owner) {
    db.update("DELETE FROM listening_attempts WHERE user_id=?", owner);
  }

  public record ExerciseSummary(String id, String title, String level, String instructions) {}
  public record ExerciseState(long id, long lessonId, String title, String level, String instructions,
      String status, String questionsJson, String transcriptEn, String translationVi,
      String scriptSegmentsJson, String ttsVoiceKey, Instant updatedAt) {}
  // Lock queries omit display fields. Nullable score remains null until submission.
  public record AttemptState(long id, long exerciseId, long draftVersion, long maxScore, Integer score,
      String status, String answersJson, String startedUtc, String title, String level, String instructions) {}
  public record RequestAttempt(long id, long exerciseId) {}
  public record AttemptId(long id) {}

  private static ExerciseSummary mapExerciseSummary(Row row) {
    return new ExerciseSummary(row.text("id"), row.text("title"), row.text("level"), row.text("instructions"));
  }

  private static ExerciseState mapExerciseState(Row row) {
    return new ExerciseState(row.number("id"), row.number("lesson_id"), row.text("title"),
        row.text("level"), row.text("instructions"), row.text("status"), row.text("questions_json"),
        row.text("transcript_en"), row.text("translation_vi"), row.text("script_segments_json"),
        row.text("tts_voice_key"), Instant.parse(row.text("updated_utc")));
  }

  private static AttemptState mapAttemptState(Row row) {
    Integer score = row.data().get("score") instanceof Number value ? value.intValue() : null;
    return new AttemptState(row.number("id"), row.number("exercise_id"), row.number("draft_version"),
        row.number("max_score"), score, row.text("status"), row.text("answers_json"),
        row.text("started_utc"), row.text("title"), row.text("level"), row.text("instructions"));
  }

  private static RequestAttempt mapRequestAttempt(Row row) {
    return new RequestAttempt(row.number("id"), row.number("exercise_id"));
  }

  private static AttemptId mapAttemptId(Row row) { return new AttemptId(row.number("id")); }
}
