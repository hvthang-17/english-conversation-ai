package vn.englishconversation.lesson;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "G2_SEED_MYSQL_TEST", matches = "true")
class StarterLessonSeedMysqlTest {
  private static final List<LessonKey> EXPECTED = List.of(
      new LessonKey(101, "INTRODUCTION", "A2", "CLUB_INTRO", "INTRODUCE_YOURSELF"),
      new LessonKey(102, "DAILY_LIFE", "A2", "DAILY_ROUTINE", "DESCRIBE_ROUTINE"),
      new LessonKey(103, "SHOPPING", "B1", "SHOP_EXCHANGE", "NEGOTIATE_EXCHANGE"),
      new LessonKey(104, "TRAVEL", "A2", "TRAIN_STATION_DIRECTIONS", "ASK_FOR_DIRECTIONS"),
      new LessonKey(105, "TRAVEL", "B1", "HOTEL_BOOKING", "BOOK_A_ROOM"),
      new LessonKey(106, "EDUCATION", "B1", "STUDY_GROUP_PLAN", "AGREE_ON_A_SCHEDULE"),
      new LessonKey(107, "WORK", "A2", "FIRST_DAY_AT_WORK", "ASK_FOR_ORIENTATION"),
      new LessonKey(108, "WORK", "B1", "INTERN_INTERVIEW", "DESCRIBE_EXPERIENCE"));

  @Test
  void freshDatabaseSeedsTheExpectedCatalogAndDoesNotReseedOnRestart() {
    var ds = testDatabase("G2_SEED_FRESH_DB_URL");
    var jdbc = new JdbcTemplate(ds);
    assertEmpty(jdbc);
    assertEquals(8, Flyway.configure().dataSource(ds).target("8").load().migrate().migrationsExecuted);
    assertTrue(scenarioInstruction(jdbc, "SHOP_EXCHANGE").contains("a medium in black on Friday"));
    assertCatalogAndRestart(ds, jdbc);
  }

  @Test
  void upgradeFromV5PreservesTheExistingLearnerInterestAndLaterCatalogEdits() {
    var ds = testDatabase("G2_SEED_UPGRADE_DB_URL");
    var jdbc = new JdbcTemplate(ds);
    assertEmpty(jdbc);
    assertEquals(5, Flyway.configure().dataSource(ds).target("5").load().migrate().migrationsExecuted);
    jdbc.update("""
        INSERT INTO users (email,password_hash,role,status,created_at,updated_at)
        VALUES ('seed-upgrade@example.test','fixture-not-a-login','LEARNER','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """);
    jdbc.update("""
        INSERT INTO learner_profiles (user_id,display_name,selected_level,created_at,updated_at)
        SELECT id,'Hồ sơ thử G2','B1',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
        FROM users WHERE email='seed-upgrade@example.test'
        """);
    jdbc.update("""
        INSERT INTO user_topic_interests (user_id,topic_id,created_at)
        SELECT u.id,t.id,UTC_TIMESTAMP(6) FROM users u CROSS JOIN topics t
        WHERE u.email='seed-upgrade@example.test' AND t.code='WORK'
        """);

    var interestsBefore = interests(jdbc);
    var topicsBefore = jdbc.queryForList("SELECT id,code,name,is_active,sort_order FROM topics ORDER BY id");
    var v5Before = jdbc.queryForList(
        "SELECT version,script,checksum FROM flyway_schema_history WHERE type='SQL' ORDER BY installed_rank");
    assertEquals(1, interestsBefore.size());
    assertEquals(2, Flyway.configure().dataSource(ds).target("7").load().migrate().migrationsExecuted);
    String editedInstruction = "Local clerk instructions written after V7.";
    jdbc.update("UPDATE scenarios SET ai_instruction=? WHERE code='SHOP_EXCHANGE'", editedInstruction);
    var v7Before = jdbc.queryForObject(
        "SELECT checksum FROM flyway_schema_history WHERE version='7'", Integer.class);
    assertEquals(1, Flyway.configure().dataSource(ds).target("8").load().migrate().migrationsExecuted);
    assertCatalogAndRestart(ds, jdbc);
    assertEquals(editedInstruction, scenarioInstruction(jdbc, "SHOP_EXCHANGE"));
    assertEquals(v7Before, jdbc.queryForObject(
        "SELECT checksum FROM flyway_schema_history WHERE version='7'", Integer.class));
    assertEquals(interestsBefore, interests(jdbc));
    assertEquals(topicsBefore, jdbc.queryForList("SELECT id,code,name,is_active,sort_order FROM topics ORDER BY id"));
    assertEquals(v5Before, jdbc.queryForList(
        "SELECT version,script,checksum FROM flyway_schema_history WHERE type='SQL' ORDER BY installed_rank LIMIT 5"));
  }

  private static List<?> interests(JdbcTemplate jdbc) {
    return jdbc.queryForList("""
        SELECT i.user_id,i.topic_id,t.code,i.created_at,p.selected_level
        FROM user_topic_interests i JOIN topics t ON t.id=i.topic_id
        JOIN learner_profiles p ON p.user_id=i.user_id
        ORDER BY i.user_id,i.topic_id
        """);
  }

  private static void assertCatalogAndRestart(DriverManagerDataSource ds, JdbcTemplate jdbc) {
    var rows = jdbc.query("""
        SELECT l.id,t.code AS topic,l.level,s.code AS scenario,l.objective_code
        FROM lessons l JOIN scenarios s ON s.id=l.scenario_id AND s.topic_id=l.topic_id
        JOIN scenario_levels sl ON sl.scenario_id=s.id AND sl.level=l.level
        JOIN topics t ON t.id=l.topic_id
        WHERE l.status='ACTIVE' AND s.status='ACTIVE' AND t.is_active=TRUE
          AND CHAR_LENGTH(TRIM(l.summary))>0 AND l.learning_objective=s.communication_goal
          AND CHAR_LENGTH(TRIM(s.learner_role))>0 AND CHAR_LENGTH(TRIM(s.ai_role))>0
          AND CHAR_LENGTH(TRIM(s.context_description))>0
        ORDER BY l.id
        """, (r, n) -> new LessonKey(r.getLong("id"), r.getString("topic"), r.getString("level"),
        r.getString("scenario"), r.getString("objective_code")));
    assertEquals(EXPECTED, rows);
    assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM lessons", Integer.class));
    assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM scenarios", Integer.class));
    assertEquals(1, jdbc.queryForObject("""
        SELECT COUNT(*) FROM scenario_role_variants v JOIN scenarios s ON s.id=v.scenario_id
        WHERE s.code='SHOP_EXCHANGE' AND v.code='REVERSE'
          AND v.learner_role=s.ai_role AND v.ai_role=s.learner_role
        """, Integer.class));
    var seedBefore = jdbc.queryForList("SELECT id,created_at,updated_at FROM lessons ORDER BY id");
    assertTrue(scenarioInstruction(jdbc, "HOTEL_BOOKING").contains("free cancellation until Thursday"));
    assertTrue(scenarioInstruction(jdbc, "STUDY_GROUP_PLAN").contains("in their own words"));
    String reverse = jdbc.queryForObject("""
        SELECT v.ai_instruction FROM scenario_role_variants v JOIN scenarios s ON s.id=v.scenario_id
        WHERE s.code='SHOP_EXCHANGE' AND v.code='REVERSE'
        """, String.class);
    assertTrue(reverse.contains("Show the receipt if asked"));
    assertTrue(reverse.contains("give the learner room to confirm the exchange"));
    var restart = Flyway.configure().dataSource(ds).target("8").load();
    assertEquals(0, restart.migrate().migrationsExecuted);
    assertEquals(seedBefore, jdbc.queryForList("SELECT id,created_at,updated_at FROM lessons ORDER BY id"));
    assertEquals(1, jdbc.queryForObject(
        "SELECT COUNT(*) FROM flyway_schema_history WHERE version='7' AND success=TRUE", Integer.class));
    assertEquals(1, jdbc.queryForObject(
        "SELECT COUNT(*) FROM flyway_schema_history WHERE version='8' AND success=TRUE", Integer.class));
  }

  private static String scenarioInstruction(JdbcTemplate jdbc, String code) {
    return jdbc.queryForObject("SELECT ai_instruction FROM scenarios WHERE code=?", String.class, code);
  }

  private static void assertEmpty(JdbcTemplate jdbc) {
    assertEquals(0, jdbc.queryForObject("""
        SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
        """, Integer.class), "Create a fresh test schema before running");
  }

  private static DriverManagerDataSource testDatabase(String variable) {
    String url = System.getenv(variable);
    if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/g2_seed_[0-9a-f]{12}\\?connectionTimeZone=UTC"))
      throw new IllegalStateException("Set " + variable + " to a dedicated local G2 seed schema");
    return new DriverManagerDataSource(url, System.getenv("SPRING_DATASOURCE_USERNAME"),
        System.getenv("SPRING_DATASOURCE_PASSWORD"));
  }

  private record LessonKey(long id, String topic, String level, String scenario, String objective) {}
}
