package vn.englishconversation.config;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import vn.englishconversation.EnglishConversationApplication;

@EnabledIfEnvironmentVariable(named = "P0_RUN_MYSQL_TEST", matches = "true")
class PhaseZeroMysqlTest {
  @Test
  void appliesV1AndRestartsOnTheSameDevelopmentSchema() throws Exception {
    String instance = checkStartup(true);
    assertEquals(instance, checkStartup(false));
  }

  @Test
  void reportsOnlyTheMissingConfigurationName() {
    var error = assertThrows(RuntimeException.class, () -> new SpringApplicationBuilder(EnglishConversationApplication.class)
        .run("--spring.profiles.active=bootstrap,p0-db", "--server.port=0", "--server.address=127.0.0.1",
             "--P0_DB_USERNAME=", "--P0_DB_PASSWORD=p0-redaction-fixture"));
    boolean missingUsername = false;
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      String message = String.valueOf(cause.getMessage());
      assertFalse(message.contains("p0-redaction-fixture"));
      missingUsername |= message.contains("Set P0_DB_USERNAME");
    }
    assertTrue(missingUsername);
  }

  private String checkStartup(boolean checkConstraints) throws Exception {
    try (var context = new SpringApplicationBuilder(EnglishConversationApplication.class)
        .run("--spring.profiles.active=bootstrap,p0-db", "--server.port=0", "--server.address=127.0.0.1")) {
      int port = ((WebServerApplicationContext) context).getWebServer().getPort();
      try (var http = HttpClient.newHttpClient()) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/system/health")).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""));
      }
      try (var connection = context.getBean(DataSource.class).getConnection()) {
        assertEquals("english_conversation", read(connection, "SELECT DATABASE()"));
        assertEquals("3306", read(connection, "SELECT @@port"));
        assertEquals("+00:00", read(connection, "SELECT @@session.time_zone"));
        assertEquals("1", read(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success"));
        assertEquals("1", read(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'SQL' AND version='1'"));
        if (checkConstraints) checkConstraints(connection);
        return read(connection, "SELECT @@server_uuid");
      }
    }
  }

  private void checkConstraints(Connection connection) throws SQLException {
    connection.setAutoCommit(false);
    try (var sql = connection.createStatement()) {
      sql.executeUpdate("""
          INSERT INTO users (email, password_hash, role, status, created_at, updated_at)
          VALUES ('p0-probe@example.invalid', 'test-only', 'LEARNER', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
          """);
      long userId = Long.parseLong(read(connection, "SELECT LAST_INSERT_ID()"));
      try (var profile = connection.prepareStatement("""
          INSERT INTO learner_profiles (user_id, display_name, selected_level, created_at, updated_at)
          VALUES (?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
          """)) {
        profile.setLong(1, userId);
        profile.setString(2, "Tiếng Anh – xin chào");
        profile.setString(3, "C9");
        assertEquals(3819, assertThrows(SQLException.class, profile::executeUpdate).getErrorCode());
        profile.setLong(1, -1);
        profile.setString(3, "A2");
        assertEquals(1452, assertThrows(SQLException.class, profile::executeUpdate).getErrorCode());
        profile.setLong(1, userId);
        assertEquals(1, profile.executeUpdate());
      }
      assertEquals("Tiếng Anh – xin chào", read(connection,
          "SELECT display_name FROM learner_profiles WHERE user_id = " + userId));
      assertEquals(1062, assertThrows(SQLException.class, () -> sql.executeUpdate("""
          INSERT INTO users (email, password_hash, role, status, created_at, updated_at)
          VALUES ('p0-probe@example.invalid', 'test-only', 'LEARNER', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
          """)).getErrorCode());
    } finally {
      connection.rollback();
    }
  }

  private String read(Connection connection, String query) throws SQLException {
    try (var sql = connection.createStatement(); var row = sql.executeQuery(query)) {
      assertTrue(row.next());
      return row.getString(1);
    }
  }
}
