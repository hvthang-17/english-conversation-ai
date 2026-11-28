package vn.englishconversation.learning.repository;

import java.util.List;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;
import vn.englishconversation.learning.dto.LearningRecommendation.Focus;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.LEARNING)
public class RecommendationRepository {
  private final JdbcStore db;

  public RecommendationRepository(JdbcStore db) {
    this.db = db;
  }

  private java.util.Optional<Row> difficultyRows(long owner, int promotion, int remediation) {
    return db.find("""
        WITH latest AS (
          SELECT a.id,a.score,a.max_score,a.grading_version,a.submitted_at,e.id AS exercise_id,
            e.questions_json,l.level,
            ROW_NUMBER() OVER (PARTITION BY l.id ORDER BY a.submitted_at DESC,a.id DESC) AS latest
          FROM listening_attempts a
          JOIN listening_exercises e ON e.id=a.exercise_id AND e.status='ACTIVE'
          JOIN lessons l ON l.id=e.lesson_id AND l.status='ACTIVE'
          JOIN topics t ON t.id=l.topic_id AND t.is_active=TRUE
          JOIN learner_profiles p ON p.user_id=a.user_id AND p.selected_level=l.level
          WHERE a.user_id=? AND a.status='SUBMITTED'
            AND JSON_LENGTH(e.questions_json)=a.max_score
        ), formats AS (
          SELECT a.id,a.exercise_id,a.level,a.score,a.max_score,a.grading_version,a.submitted_at,
                 GROUP_CONCAT(q.kind ORDER BY q.kind SEPARATOR ',') AS question_format
          FROM latest a
          JOIN JSON_TABLE(a.questions_json, '$[*]' COLUMNS(kind VARCHAR(32) PATH '$.type')) q
          WHERE a.latest=1 AND q.kind IN ('MULTIPLE_CHOICE','FILL_BLANK','REORDER','DICTATION')
          GROUP BY a.id,a.exercise_id,a.level,a.score,a.max_score,a.grading_version,a.submitted_at
          HAVING COUNT(*)=a.max_score
        ), ranked AS (
          SELECT *,ROW_NUMBER() OVER (
            PARTITION BY grading_version,question_format ORDER BY submitted_at DESC,id DESC
          ) AS sample_rank
          FROM formats
        )
        SELECT level FROM ranked WHERE sample_rank<=3
        GROUP BY level,grading_version,question_format
        HAVING COUNT(*)=3 AND (
          (level='A2' AND MIN(score*100 >= max_score*?)=1)
          OR (level='B1' AND MIN(score*100 < max_score*?)=1)
        )
        ORDER BY MAX(submitted_at) DESC,MAX(id) DESC
        LIMIT 1
        """, owner, promotion, remediation);
  }

  private List<Row> cardsRows(long owner) {
    return db.rows("""
        SELECT id,expression FROM review_items
        WHERE user_id=? AND status='NEEDS_REVIEW'
        ORDER BY last_reviewed_at,created_at,id LIMIT 4
        """, owner);
  }

  private List<Row> lessonsRows(long owner, Focus focus, int limit) {
    return db.rows("""
        WITH recent_listening AS (
          SELECT exercise_id,score,max_score,
            ROW_NUMBER() OVER (PARTITION BY exercise_id ORDER BY submitted_at DESC,id DESC) AS attempt_no
          FROM listening_attempts WHERE user_id=? AND status='SUBMITTED'
        ), weak_listening AS (
          SELECT exercise_id FROM recent_listening WHERE attempt_no<=2
          GROUP BY exercise_id HAVING COUNT(*)=2 AND MAX(score / max_score)<0.60
        ), repeated_errors AS (
          SELECT s.topic_id,f.rule_code FROM practice_sessions p
          JOIN scenarios s ON s.id=p.scenario_id
          JOIN session_feedback sf ON sf.session_id=p.id AND sf.status='SUCCEEDED'
          JOIN feedback_items f ON f.feedback_id=sf.id AND f.item_type='ERROR'
          WHERE p.user_id=?
          GROUP BY s.topic_id,f.rule_code HAVING COUNT(DISTINCT f.source_message_id)>=2
        ), candidates AS (
          SELECT l.id,l.title,l.level,t.name AS topic,
            EXISTS(SELECT 1 FROM weak_listening w JOIN listening_exercises e ON e.id=w.exercise_id
              WHERE e.lesson_id=l.id AND e.status='ACTIVE') AS listening_gap,
            (EXISTS(SELECT 1 FROM repeated_errors r WHERE r.topic_id=l.topic_id)
              AND s.status='ACTIVE' AND st.is_active=TRUE) AS conversation_gap,
            (i.user_id IS NOT NULL) AS interested,
            EXISTS(SELECT 1 FROM learning_plans lp JOIN learning_plan_items li ON li.plan_id=lp.id
              WHERE lp.user_id=p.user_id AND li.lesson_id=l.id AND li.status='COMPLETED') AS completed_in_plan,
            CASE ?
              WHEN 'LISTENING' THEN EXISTS(SELECT 1 FROM listening_exercises e WHERE e.lesson_id=l.id AND e.status='ACTIVE')
              WHEN 'PRONUNCIATION' THEN EXISTS(SELECT 1 FROM pronunciation_drills d WHERE d.lesson_id=l.id AND d.status='ACTIVE')
              WHEN 'CONVERSATION' THEN COALESCE(s.status='ACTIVE' AND st.is_active=TRUE,FALSE)
              WHEN 'REVIEW' THEN EXISTS(SELECT 1 FROM language_items v WHERE v.lesson_id=l.id AND v.status='ACTIVE')
              ELSE FALSE
            END AS focus_match
          FROM learner_profiles p
          JOIN lessons l ON l.level=p.selected_level AND l.status='ACTIVE'
          JOIN topics t ON t.id=l.topic_id AND t.is_active=TRUE
          LEFT JOIN scenarios s ON s.id=l.scenario_id
          LEFT JOIN topics st ON st.id=s.topic_id
          LEFT JOIN user_topic_interests i ON i.user_id=p.user_id AND i.topic_id=l.topic_id
          WHERE p.user_id=?
        )
        SELECT id,title,level,topic,listening_gap,COALESCE(conversation_gap,FALSE) AS conversation_gap,
               interested,focus_match
        FROM candidates
        ORDER BY (listening_gap OR COALESCE(conversation_gap,FALSE)) DESC,completed_in_plan,focus_match DESC,interested DESC,id
        LIMIT ?
        """, owner, owner, focus == null ? "" : focus.name(), owner, limit);
  }

  public record Difficulty(String level) {}
  private static Difficulty mapDifficulty(Row row) { return new Difficulty(row.text("level")); }
  public record CardSource(String id,String expression) {}
  private static CardSource mapCardSource(Row row) { return new CardSource(row.text("id"),row.text("expression")); }
  public record LessonSource(String id,String title,String level,String topic,boolean listeningGap,boolean conversationGap,boolean focusMatch,boolean interested) {}
  private static LessonSource mapLessonSource(Row row) { return new LessonSource(row.text("id"),row.text("title"),row.text("level"),row.text("topic"),row.flag("listening_gap"),row.flag("conversation_gap"),row.flag("focus_match"),row.flag("interested")); }
  public java.util.Optional<Difficulty> difficulty(long owner,int promotion,int remediation) { return difficultyRows(owner,promotion,remediation).map(RecommendationRepository::mapDifficulty); }
  public java.util.List<CardSource> cards(long owner) { return cardsRows(owner).stream().map(RecommendationRepository::mapCardSource).toList(); }
  public java.util.List<LessonSource> lessons(long owner,Focus focus,int limit) { return lessonsRows(owner,focus,limit).stream().map(RecommendationRepository::mapLessonSource).toList(); }
}
