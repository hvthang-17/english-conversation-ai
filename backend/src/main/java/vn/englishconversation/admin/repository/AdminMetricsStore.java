package vn.englishconversation.admin.repository;

import static vn.englishconversation.admin.dto.AdminMetrics.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;

@Repository
@Profile("g6")
public class AdminMetricsStore {
  private final JdbcStore db;

  public AdminMetricsStore(JdbcStore db) { this.db=db; }

  public Accounts accounts(LocalDateTime from,LocalDateTime until) {
    var row=db.find("""
        SELECT COUNT(CASE WHEN role='LEARNER' THEN 1 END) AS learners,
          COUNT(CASE WHEN role='ADMIN' THEN 1 END) AS admins,
          COUNT(CASE WHEN status='LOCKED' THEN 1 END) AS locked,
          COUNT(CASE WHEN created_at>=? AND created_at<? THEN 1 END) AS registered
        FROM users WHERE deletion_requested_at IS NULL
        """,from,until).orElseThrow();
    return new Accounts(row.number("learners"),row.number("admins"),row.number("locked"),row.number("registered"));
  }

  public Learning learning(LocalDateTime from,LocalDateTime until) {
    var row=db.find("""
        SELECT
          (SELECT COUNT(*) FROM messages WHERE sender='LEARNER' AND created_at>=? AND created_at<?) AS turns,
          (SELECT COUNT(*) FROM listening_attempts WHERE status='SUBMITTED' AND submitted_at>=? AND submitted_at<?) AS listening,
          (SELECT COUNT(*) FROM pronunciation_attempts WHERE drill_id IS NOT NULL AND status='SUCCEEDED'
             AND scored_at>=? AND scored_at<?) AS pronunciation,
          (SELECT COUNT(*) FROM review_attempts WHERE reviewed_at>=? AND reviewed_at<?) AS reviews,
          COUNT(CASE WHEN status='COMPLETED' THEN 1 END) AS completed,COUNT(*) AS ended
        FROM practice_sessions WHERE status IN ('COMPLETED','ABANDONED') AND ended_at>=? AND ended_at<?
        """,from,until,from,until,from,until,from,until,from,until).orElseThrow();
    return new Learning(row.number("turns"),row.number("listening"),row.number("pronunciation"),
        row.number("reviews"),row.number("completed"),row.number("ended"));
  }

  public List<Content> popularContent(LocalDateTime from,LocalDateTime until) {
    return db.rows("""
        SELECT * FROM (
          SELECT 'LESSON' AS kind,l.id,l.title,COUNT(*) AS uses
          FROM lesson_completions c JOIN lessons l ON l.id=c.lesson_id
          WHERE c.completed_at>=? AND c.completed_at<? GROUP BY l.id,l.title
          UNION ALL
          SELECT 'SCENARIO',s.id,s.title,COUNT(*) FROM practice_sessions p JOIN scenarios s ON s.id=p.scenario_id
          WHERE p.started_at>=? AND p.started_at<? GROUP BY s.id,s.title
          UNION ALL
          SELECT 'LISTENING',e.id,e.title,COUNT(*) FROM listening_attempts a JOIN listening_exercises e ON e.id=a.exercise_id
          WHERE a.status='SUBMITTED' AND a.submitted_at>=? AND a.submitted_at<? GROUP BY e.id,e.title
          UNION ALL
          SELECT 'PRONUNCIATION',d.id,l.title,COUNT(*) FROM pronunciation_attempts a
          JOIN pronunciation_drills d ON d.id=a.drill_id JOIN lessons l ON l.id=d.lesson_id
          WHERE a.status='SUCCEEDED' AND a.scored_at>=? AND a.scored_at<? GROUP BY d.id,l.title
        ) totals ORDER BY uses DESC,kind,id LIMIT 20
        """,from,until,from,until,from,until,from,until).stream()
        .map(r -> new Content(r.text("kind"),r.text("id"),r.text("title"),r.number("uses"))).toList();
  }

  public Catalog catalog() {
    var row=db.find("""
        SELECT (SELECT COUNT(*) FROM topics WHERE is_active) AS topics,COUNT(*) AS scenarios
        FROM scenarios s JOIN topics t ON t.id=s.topic_id WHERE t.is_active AND s.status='ACTIVE'
          AND EXISTS (SELECT 1 FROM scenario_levels l WHERE l.scenario_id=s.id)
        """).orElseThrow();
    return new Catalog(row.number("topics"),row.number("scenarios"));
  }

  public List<Ai> ai(LocalDateTime from,LocalDateTime until) {
    return db.rows("""
        SELECT r.task_type,a.provider,COUNT(*) AS attempts,COUNT(CASE WHEN a.attempt_no>1 THEN 1 END) AS retries,
          COUNT(CASE WHEN a.status='SUCCEEDED' THEN 1 END) AS succeeded,
          COUNT(CASE WHEN a.status='FAILED' THEN 1 END) AS failed,
          COUNT(CASE WHEN a.status='UNKNOWN' THEN 1 END) AS unknown,
          COUNT(CASE WHEN a.status='RUNNING' THEN 1 END) AS running,
          COUNT(CASE WHEN a.error_code LIKE '%QUOTA%' OR a.error_code LIKE '%RATE_LIMIT%' OR a.error_code='HTTP_429'
             THEN 1 END) AS quota_errors,
          AVG(a.latency_ms) AS latency,SUM(a.input_tokens) AS input_tokens,SUM(a.output_tokens) AS output_tokens,
          COUNT(a.latency_ms) AS latency_samples,COUNT(a.input_tokens) AS input_samples,COUNT(a.output_tokens) AS output_samples
        FROM ai_attempts a JOIN ai_requests r ON r.id=a.ai_request_id
        WHERE a.started_at>=? AND a.started_at<? GROUP BY r.task_type,a.provider ORDER BY a.provider,r.task_type
        """,from,until).stream().map(r -> new Ai(r.text("task_type"),r.text("provider"),r.number("attempts"),
        r.number("retries"),r.number("succeeded"),r.number("failed"),r.number("unknown"),r.number("running"),
        r.number("quota_errors"),(BigDecimal)r.data().get("latency"),nullable(r,"input_tokens"),nullable(r,"output_tokens"),
        r.number("latency_samples"),r.number("input_samples"),r.number("output_samples"))).toList();
  }

  private static Long nullable(Row row,String field) {
    return row.data().get(field)==null ? null : row.number(field);
  }
}
