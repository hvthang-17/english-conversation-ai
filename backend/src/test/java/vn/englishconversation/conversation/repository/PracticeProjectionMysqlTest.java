package vn.englishconversation.conversation.repository;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.englishconversation.common.repository.JdbcStore;

class PracticeProjectionMysqlTest {
  @Test
  @EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
  void mapsNullableUtcMicrosecondsAndBigintWithoutDependingOnJvmTimeZone() {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    assertNotNull(url);
    assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:3307/phase2_auth_[0-9]{14}\\?connectionTimeZone=UTC"));
    var ds = new DriverManagerDataSource(url, System.getenv("SPRING_DATASOURCE_USERNAME"),
        System.getenv("SPRING_DATASOURCE_PASSWORD"));
    org.flywaydb.core.Flyway.configure().dataSource(ds).target("33").load().migrate();
    var jdbc = new JdbcTemplate(ds);
    var store = new JdbcStore(jdbc);
    var repository = new PracticeRepository(store, jdbc);
    new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx -> {
      tx.setRollbackOnly();
      long owner = store.insert("""
          INSERT INTO users(email,password_hash,role,status,created_at,updated_at)
          VALUES (?,?,'LEARNER','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
          """, "projection-" + UUID.randomUUID() + "@example.test", "synthetic-hash");
      long scenario = jdbc.queryForObject("SELECT id FROM scenarios WHERE code='CLUB_INTRO'", Long.class);
      long id = store.insert("""
          INSERT INTO practice_sessions(user_id,scenario_id,client_request_id,request_fingerprint,
          selected_level,scenario_snapshot,status,started_at,last_activity_at,version)
          VALUES (?,?,'projection',REPEAT('a',64),'A2',JSON_OBJECT(),'ACTIVE',
          '2026-11-28 01:02:03.123456','2026-11-28 01:02:03.123456',3000000000)
          """, owner, scenario);
      var active = repository.ownedSession(id, owner, true).orElseThrow();
      assertEquals(3_000_000_000L, active.version());
      assertEquals(Instant.parse("2026-11-28T01:02:03.123456Z"), active.startedAt());
      assertNull(active.endedAt());
      assertTrue(repository.ownedSession(id, owner + 1, false).isEmpty());
      jdbc.update("UPDATE practice_sessions SET ended_at='2026-11-28 02:03:04.654321' WHERE id=?", id);
      assertEquals(Instant.parse("2026-11-28T02:03:04.654321Z"), repository.session(id).endedAt());
    });
  }
}
