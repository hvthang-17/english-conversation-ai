package vn.englishconversation.statistics.repository;

import static vn.englishconversation.learning.dto.LearningData.*;

import java.util.List;
import java.util.Optional;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.LEARNING)
public class LearningStatisticsRepository {
  private final JdbcStore db;

  public LearningStatisticsRepository(JdbcStore db) {
    this.db = db;
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
            .map(LearningStatisticsRepository::group)
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
            .map(LearningStatisticsRepository::group)
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
