package vn.englishconversation.scenario.repository;

import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;

/**
 * Snapshot source reads use FOR SHARE in the caller's reservation transaction.
 * A role variant has no title; ScenarioSource.title is null for that projection.
 */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class PracticeScenarioRepository {
  private final JdbcStore db;
  public PracticeScenarioRepository(JdbcStore db) { this.db = db; }

  public record ScenarioSource(
      String title,
      String learnerRole,
      String aiRole,
      String communicationGoal,
      String contextDescription,
      String aiInstruction
  ) {}

  public java.util.Optional<ScenarioSource> availableScenario(long scenario,String level) {
    return db.find("""
SELECT s.* FROM scenarios s JOIN topics t ON t.id=s.topic_id
JOIN scenario_levels l ON l.scenario_id=s.id
WHERE s.id=? AND l.level=? AND t.is_active=TRUE AND s.status='ACTIVE' FOR SHARE
""", scenario, level).map(row -> new ScenarioSource(row.text("title"), row.text("learner_role"), row.text("ai_role"), row.text("communication_goal"), row.text("context_description"), row.text("ai_instruction")));
  }

  public java.util.Optional<ScenarioSource> roleVariant(long scenario,String code) {
    return db.find("""
                  SELECT learner_role,ai_role,communication_goal,context_description,ai_instruction
                  FROM scenario_role_variants WHERE scenario_id=? AND code=? FOR SHARE
                  """, scenario, code).map(row -> new ScenarioSource(null, row.text("learner_role"), row.text("ai_role"), row.text("communication_goal"), row.text("context_description"), row.text("ai_instruction")));
  }
}
