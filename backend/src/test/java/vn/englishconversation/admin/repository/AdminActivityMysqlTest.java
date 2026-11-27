package vn.englishconversation.admin.repository;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import vn.englishconversation.common.repository.JdbcStore;

@EnabledIfEnvironmentVariable(named = "ACTIVITY_DB_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:.*")
class AdminActivityMysqlTest {
  @Test
  void pagesAndFiltersMetadataOnMysqlWithoutChangingPersistentTables() throws Exception {
    try (var connection = DriverManager.getConnection(System.getenv("ACTIVITY_DB_URL"),
        System.getenv("ACTIVITY_DB_USER"), System.getenv("ACTIVITY_DB_PASSWORD"))) {
      // Temporary tables shadow application tables only on this isolated connection.
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("CREATE TEMPORARY TABLE admin_audit_logs (id BIGINT PRIMARY KEY,action VARCHAR(80),target_type VARCHAR(40),target_id BIGINT,actor_user_id BIGINT,created_at DATETIME(6)) CHARACTER SET utf8mb4");
      jdbc.execute("CREATE TEMPORARY TABLE ai_requests (id BIGINT PRIMARY KEY,task_type VARCHAR(40))");
      jdbc.execute("CREATE TEMPORARY TABLE ai_attempts (id BIGINT PRIMARY KEY,ai_request_id BIGINT,provider VARCHAR(80),model_name VARCHAR(100),status VARCHAR(30),latency_ms BIGINT,started_at DATETIME(6)) CHARACTER SET utf8mb4");
      jdbc.update("INSERT INTO ai_requests VALUES (1,'CHAT')");
      for (int id = 1; id <= 27; id++) jdbc.update("INSERT INTO ai_attempts VALUES (?,1,'Gemini',?,?,0,'2026-11-26 04:00:00.123456')",
          id, id == 1 ? "100%_fixture" : "fixture-model", id % 3 == 0 ? "FAILED" : "SUCCEEDED");
      for (int id = 1; id <= 15; id++) jdbc.update("INSERT INTO admin_audit_logs VALUES (?,'TOPIC_CREATED','TOPIC',?,NULL,'2026-11-26 04:00:00')", id, id);
      var store = new AdminActivityStore(new JdbcStore(jdbc));
      var first = store.list(false, 0, 12, "", "");
      var second = store.list(false, 1, 12, "", "");
      assertEquals(27, first.total());
      assertEquals("27", first.items().getFirst().id());
      assertEquals("15", second.items().getFirst().id());
      assertEquals(3, store.list(false, 2, 12, "", "").items().size());
      assertTrue(store.list(false, 3, 12, "", "").items().isEmpty());
      var failed = store.list(false, 2, 4, "Hội thoại", "FAILED");
      assertEquals(9, failed.total());
      assertEquals("3", failed.items().getFirst().id());
      assertEquals(1, store.list(false, 0, 12, "%_", "").total());
      assertEquals(0, store.list(false, 0, 12, "' OR 1=1 --", "").total());
      var audit = store.list(true, 1, 12, "Tạo chủ đề", "");
      assertEquals(15, audit.total());
      assertEquals(3, audit.items().size());
      assertNull(audit.items().getFirst().actor());
    }
  }
}
