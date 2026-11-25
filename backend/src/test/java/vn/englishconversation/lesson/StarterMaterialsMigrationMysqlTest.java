package vn.englishconversation.lesson;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "G3_UPGRADE_MYSQL_TEST", matches = "true")
class StarterMaterialsMigrationMysqlTest {
  @Test
  void upgradeFromV8PreservesHistoryBookmarksAndInterestsAcrossRestart() {
    String url = System.getenv("G3_UPGRADE_DB_URL");
    if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/g3_test_[0-9a-f]{12}\\?connectionTimeZone=UTC"))
      throw new IllegalStateException("Use a dedicated G3 upgrade schema");
    var source = new DriverManagerDataSource(url, System.getenv("SPRING_DATASOURCE_USERNAME"),
        System.getenv("SPRING_DATASOURCE_PASSWORD"));
    var db = new JdbcTemplate(source);
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class));
    assertEquals(8, Flyway.configure().dataSource(source).target("8").load().migrate().migrationsExecuted);
    db.update("""
        INSERT INTO users(email,password_hash,role,status,created_at,updated_at)
        VALUES ('g3-upgrade@example.test','fixture-not-for-login','LEARNER','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """);
    long user = db.queryForObject("SELECT id FROM users WHERE email='g3-upgrade@example.test'", Long.class);
    db.update("""
        INSERT INTO user_topic_interests(user_id,topic_id,created_at)
        SELECT ?,id,UTC_TIMESTAMP(6) FROM topics WHERE code='WORK'
        """, user);
    db.update("""
        INSERT INTO lesson_bookmarks(user_id,lesson_id,kind,created_at)
        VALUES (?,105,'FAVORITE',UTC_TIMESTAMP(6))
        """, user);

    String history = "SELECT version,script,checksum FROM flyway_schema_history ORDER BY installed_rank LIMIT 8";
    String bookmarks = "SELECT * FROM lesson_bookmarks ORDER BY user_id,lesson_id,kind";
    String interests = "SELECT * FROM user_topic_interests ORDER BY user_id,topic_id";
    String catalog = "SELECT id,topic_id,scenario_id FROM lessons ORDER BY id";
    var before = db.queryForList(history);
    var saved = db.queryForList(bookmarks);
    var topics = db.queryForList(interests);
    var lessons = db.queryForList(catalog);
    assertEquals(2, Flyway.configure().dataSource(source).target("10").load().migrate().migrationsExecuted);
    var materialIds = db.queryForList("SELECT id,lesson_id,expression FROM language_items ORDER BY id");
    assertEquals(40, materialIds.size());
    assertEquals(8, db.queryForObject("SELECT COUNT(*) FROM sample_dialogues", Integer.class));
    assertEquals(0, Flyway.configure().dataSource(source).target("10").load().migrate().migrationsExecuted);
    assertEquals(materialIds, db.queryForList("SELECT id,lesson_id,expression FROM language_items ORDER BY id"));
    assertEquals(before, db.queryForList(history));
    assertEquals(saved, db.queryForList(bookmarks));
    assertEquals(topics, db.queryForList(interests));
    assertEquals(lessons, db.queryForList(catalog));
  }
}
