package vn.englishconversation.statistics;


import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.englishconversation.conversation.dto.*;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class LearningStatisticsIntegrationMysqlTest extends FullSystemMysqlFixture {

  @Test
  void statisticsCountLearnerMessagesAcrossRetryAndVietnamDayBoundary() {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    provider.timeout = true;
    var pending =
        service.send(owner, id, new SendTextRequest("I want order soup.", "stats-turn-001"));
    service.retry(owner, id, Long.parseLong(pending.operation().id()), new RetryRequest(true));
    var replay =
        service.send(owner, id, new SendTextRequest("I want order soup.", "stats-turn-001"));
    var learner = replay.messages().get(1);
    service.hint(owner, id, new HintRequest(replay.messages().getLast().id(), "stats-hint-001"));
    service.finish(owner, id);
    String correction =
        """
[{"sourceMessageId":"%s","itemType":"ERROR","category":"GRAMMAR","ruleCode":"VERB_FORM",
"originalText":"want order","suggestedText":"want to order","explanationVi":"Dùng want to."},
{"sourceMessageId":"%s","itemType":"IMPROVEMENT","category":"EXPRESSION","ruleCode":"POLITENESS",
"originalText":"I want order soup.","suggestedText":"I would like soup, please.","explanationVi":"Cách nói lịch sự hơn."}]
"""
            .formatted(learner.id(), learner.id());
    provider.feedbackOutput =
        CLEAN_REVIEW.replace("\"corrections\":[]", "\"corrections\":" + correction);
    assertEquals("SUCCEEDED", service.generateFeedback(owner, id, false, false).status());
    jdbc.update(
        "UPDATE messages SET created_at='2026-11-20 17:02:00' WHERE id=?",
        Long.parseLong(learner.id()));
    jdbc.update("UPDATE learner_profiles SET selected_level='B1' WHERE user_id=?", owner.id());
    jdbc.update(
        "INSERT INTO user_topic_interests(user_id,topic_id,created_at) SELECT"
            + " ?,s.topic_id,UTC_TIMESTAMP(6) FROM scenarios s JOIN scenario_levels l ON l.scenario_id=s.id"
            + " JOIN topics t ON t.id=s.topic_id WHERE l.level='B1' AND s.status='ACTIVE' AND t.is_active=TRUE"
            + " ORDER BY s.id LIMIT 1",
        owner.id());
    var stats = learning.statistics(owner);
    assertEquals(1, stats.completedSessions());
    assertEquals(1, stats.learnerTurns());
    assertEquals(1, stats.practiceDays());
    assertEquals("2026-11-21", stats.recentDays().getFirst().label());
    assertEquals(1, stats.topics().getFirst().learnerTurns());
    assertEquals(
        java.util.List.of(new vn.englishconversation.learning.dto.LearningData.Group("GRAMMAR", 1)),
        stats.errorGroups());
    assertEquals("B1", stats.recommendations().getFirst().level());
    assertEquals("Chủ đề bạn quan tâm", stats.recommendations().getFirst().reason());
    learning.deleteAll(owner);
    assertEquals(0, learning.statistics(owner).completedSessions());
    assertEquals(0, learning.statistics(owner).learnerTurns());
    assertTrue(learning.statistics(owner).errorGroups().isEmpty());
    assertEquals(0, learning.history(owner, 0, 12).total());
  }
}
