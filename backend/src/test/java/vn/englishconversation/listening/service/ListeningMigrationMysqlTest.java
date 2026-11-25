package vn.englishconversation.listening.service;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "G4_UPGRADE_MYSQL_TEST", matches = "true")
class ListeningMigrationMysqlTest {
  @Test
  void upgradesG3AndOnlyCreatesDrafts() {
    String url = System.getenv("G4_UPGRADE_DB_URL");
    if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:(3306|13306)/g4_test_[0-9a-f]{12}\\?connectionTimeZone=UTC"))
      throw new IllegalStateException("Use a dedicated G4 upgrade schema");
    var source = new DriverManagerDataSource(url, System.getenv("SPRING_DATASOURCE_USERNAME"),
        System.getenv("SPRING_DATASOURCE_PASSWORD"));
    var db = new JdbcTemplate(source);
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class));
    assertEquals(10, Flyway.configure().dataSource(source).target("10").load().migrate().migrationsExecuted);
    String starterItems = "SELECT id,lesson_id,expression FROM language_items ORDER BY id";
    var before = db.queryForList(starterItems);
    db.update("""
        INSERT INTO users(email,password_hash,role,status,created_at,updated_at)
        VALUES ('g4-upgrade@example.test','fixture','LEARNER','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """);
    db.update("""
        INSERT INTO lesson_bookmarks(user_id,lesson_id,kind,created_at)
        SELECT id,105,'FAVORITE',UTC_TIMESTAMP(6) FROM users WHERE email='g4-upgrade@example.test'
        """);
    var g4 = Flyway.configure().dataSource(source).target("12").load();
    assertEquals(2, g4.migrate().migrationsExecuted);
    assertEquals(8, db.queryForObject("SELECT COUNT(*) FROM listening_exercises WHERE status='DRAFT'", Integer.class));
    assertEquals(4, db.queryForObject("SELECT COUNT(*) FROM listening_exercises WHERE script_segments_json IS NOT NULL", Integer.class));
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM lesson_bookmarks", Integer.class));
    assertEquals(before, db.queryForList(starterItems));
    assertEquals(0, g4.migrate().migrationsExecuted);
  }
}
