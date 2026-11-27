package vn.englishconversation.learning.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;

@Repository
@Profile("g6")
public class ProgressRepository {
  private final JdbcStore db;
  public ProgressRepository(JdbcStore db) { this.db = db; }

  public void clear(long owner) {
    db.update("DELETE FROM lesson_completions WHERE user_id=?", owner);
    db.update("DELETE FROM study_intervals WHERE user_id=?", owner);
    db.update("DELETE FROM user_badges WHERE user_id=?", owner);
    db.update("DELETE FROM notifications WHERE user_id=? AND kind='RESULT'", owner);
  }

  public Optional<Row> lesson(long id) {
    return db.find("""
        SELECT l.id, (EXISTS(SELECT 1 FROM listening_exercises e WHERE e.lesson_id=l.id AND e.status='ACTIVE')
          OR EXISTS(SELECT 1 FROM pronunciation_drills d WHERE d.lesson_id=l.id AND d.status='ACTIVE')) AS assessed,
          (EXISTS(SELECT 1 FROM language_items i WHERE i.lesson_id=l.id AND i.status='ACTIVE')
          OR EXISTS(SELECT 1 FROM sample_dialogues d WHERE d.lesson_id=l.id AND d.status='ACTIVE')) AS has_materials
        FROM lessons l JOIN topics t ON t.id=l.topic_id
        WHERE l.id=? AND l.status='ACTIVE' AND t.is_active FOR SHARE
        """, id);
  }

  public boolean completed(long owner, long lesson) {
    return db.count("SELECT COUNT(*) FROM lesson_completions WHERE user_id=? AND lesson_id=?", owner, lesson) != 0;
  }

  public void complete(long owner, long lesson) {
    db.update("""
        INSERT INTO lesson_completions VALUES(?,?,UTC_TIMESTAMP(6),'lesson-completion-v1')
        ON DUPLICATE KEY UPDATE completed_at=lesson_completions.completed_at
        """, owner, lesson);
  }

  public Optional<Row> listeningLesson(long owner, long attempt) {
    return db.find("""
        SELECT e.lesson_id FROM listening_attempts a JOIN listening_exercises e ON e.id=a.exercise_id
        WHERE a.id=? AND a.user_id=? AND a.status='SUBMITTED'
        """, attempt, owner);
  }

  public Optional<Row> pronunciationLesson(long owner, long attempt) {
    return db.find("""
        SELECT d.lesson_id FROM pronunciation_attempts a JOIN pronunciation_drills d ON d.id=a.drill_id
        WHERE a.id=? AND a.user_id=? AND a.status='SUCCEEDED' AND a.overall_score IS NOT NULL
        """, attempt, owner);
  }

  public Optional<Instant> previous(long owner, String stream) {
    return db.find("SELECT DATE_FORMAT(ended_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS ended_at FROM study_intervals WHERE user_id=? AND stream_id=? ORDER BY id DESC LIMIT 1", owner, stream)
        .map(row -> Instant.parse(row.text("ended_at")));
  }

  public void interval(long owner, String event, String stream, String kind, Instant start, Instant end) {
    db.update("""
        INSERT INTO study_intervals(user_id,client_event_id,stream_id,activity_kind,started_at,ended_at)
        VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=study_intervals.id
        """, owner, event, stream, kind, Timestamp.from(start), Timestamp.from(end));
  }

  public List<Row> intervals(long owner) {
    return db.rows("SELECT DATE_FORMAT(started_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS started_at,DATE_FORMAT(ended_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS ended_at FROM study_intervals WHERE user_id=? AND started_at<ended_at ORDER BY study_intervals.started_at,id", owner);
  }

  public Row profile(long owner) {
    return db.find("""
        SELECT p.selected_level,p.daily_goal_minutes,COALESCE(s.time_zone,'Asia/Ho_Chi_Minh') AS time_zone
        FROM learner_profiles p LEFT JOIN practice_schedules s ON s.user_id=p.user_id WHERE p.user_id=?
        """, owner).orElseThrow();
  }

  public long completions(long owner) {
    return db.count("SELECT COUNT(*) FROM lesson_completions WHERE user_id=?", owner);
  }

  public List<Row> completionHistory(long owner) {
    return db.rows("""
        SELECT l.id,l.title,(l.status='ACTIVE' AND t.is_active) AS available,
          DATE_FORMAT(c.completed_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS time
        FROM lesson_completions c JOIN lessons l ON l.id=c.lesson_id JOIN topics t ON t.id=l.topic_id
        WHERE c.user_id=? ORDER BY c.completed_at DESC,c.lesson_id DESC LIMIT 20
        """, owner);
  }

  public long tasks(long owner) {
    return db.count("""
        SELECT COUNT(*) FROM learning_plan_items i JOIN learning_plans p ON p.id=i.plan_id
        WHERE p.user_id=? AND i.status='COMPLETED'
        """, owner);
  }

  public List<Row> topics(long owner, String level) {
    return db.rows("""
        SELECT t.id,t.name,COUNT(l.id) AS total,COUNT(c.lesson_id) AS completed
        FROM topics t LEFT JOIN lessons l ON l.topic_id=t.id AND l.status='ACTIVE' AND l.level=?
        LEFT JOIN lesson_completions c ON c.lesson_id=l.id AND c.user_id=?
        WHERE t.is_active GROUP BY t.id,t.name,t.sort_order ORDER BY t.sort_order,t.id
        """, level, owner);
  }

  public List<Row> listening(long owner) {
    return db.rows("""
        SELECT a.id,e.title,l.level,a.score,a.max_score,a.grading_version,
          DATE_FORMAT(a.submitted_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS time,
          (SELECT GROUP_CONCAT(q.kind ORDER BY q.kind) FROM JSON_TABLE(e.questions_json,
            '$[*]' COLUMNS(kind VARCHAR(32) PATH '$.type')) q) AS question_format
        FROM listening_attempts a JOIN listening_exercises e ON e.id=a.exercise_id
        JOIN lessons l ON l.id=e.lesson_id WHERE a.user_id=? AND a.status='SUBMITTED'
        ORDER BY a.submitted_at DESC,a.id DESC LIMIT 100
        """, owner);
  }

  public List<Row> pronunciation(long owner) {
    return db.rows("""
        SELECT id,mode,drill_mode,locale,assessment_version,provider,overall_score,status,
          DATE_FORMAT(COALESCE(scored_at,created_at),'%Y-%m-%dT%H:%i:%s.%fZ') AS time
        FROM pronunciation_attempts WHERE user_id=? AND status IN ('SUCCEEDED','INSUFFICIENT_EVIDENCE','FAILED','UNKNOWN')
        ORDER BY COALESCE(scored_at,created_at) DESC,id DESC LIMIT 100
        """, owner);
  }

  public List<Row> repeatedErrors(long owner) {
    return db.rows("""
        SELECT i.rule_code,i.category,COUNT(DISTINCT i.source_message_id) AS sentences
        FROM feedback_items i JOIN session_feedback f ON f.id=i.feedback_id AND f.status='SUCCEEDED'
        JOIN practice_sessions p ON p.id=i.session_id
        WHERE p.user_id=? AND i.item_type='ERROR'
        GROUP BY i.rule_code,i.category HAVING COUNT(DISTINCT i.source_message_id)>=2
        ORDER BY sentences DESC,i.rule_code,i.category LIMIT 10
        """, owner);
  }

  public void badge(long owner, String code) {
    db.update("INSERT IGNORE INTO user_badges VALUES(?,?,UTC_TIMESTAMP(6))", owner, code);
  }

  public List<String> badges(long owner) {
    return db.rows("SELECT badge_code FROM user_badges WHERE user_id=? ORDER BY earned_at,badge_code", owner)
        .stream().map(row -> row.text("badge_code")).toList();
  }

  public void notify(long owner, String kind, String key, String title, String body) {
    db.update("""
        INSERT IGNORE INTO notifications(user_id,kind,dedupe_key,title,body,created_at)
        VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))
        """, owner, kind, key, title, body);
  }

  private static final String VISIBLE_NOTIFICATION = """
      n.user_id=? AND (n.kind<>'ANNOUNCEMENT' OR EXISTS (
        SELECT 1 FROM announcements a WHERE a.id=n.announcement_id AND a.status='PUBLISHED'
          AND a.published_at<=UTC_TIMESTAMP(6) AND (a.expires_at IS NULL OR a.expires_at>UTC_TIMESTAMP(6))
      )) AND (n.kind<>'SUPPORT' OR EXISTS (
        SELECT 1 FROM support_tickets s WHERE s.id=n.support_ticket_id AND s.user_id=n.user_id
      ))
      """;

  public List<Row> notifications(long owner, int page) {
    return db.rows("""
        SELECT id,kind,title,body,support_ticket_id,(read_at IS NOT NULL) AS is_read,
          DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS time
        FROM notifications n WHERE
        """ + VISIBLE_NOTIFICATION + " ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?", owner, page * 20);
  }

  public boolean read(long owner, long id) {
    if (db.count("SELECT COUNT(*) FROM notifications n WHERE n.id=? AND " + VISIBLE_NOTIFICATION, id, owner)==0) return false;
    db.update("UPDATE notifications n SET read_at=COALESCE(read_at,UTC_TIMESTAMP(6)) WHERE n.id=? AND " + VISIBLE_NOTIFICATION,
        id, owner);
    return true;
  }
}
