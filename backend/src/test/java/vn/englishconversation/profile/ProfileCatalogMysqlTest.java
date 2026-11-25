package vn.englishconversation.profile;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "PHASE1_MYSQL_TEST", matches = "true")
class ProfileCatalogMysqlTest {
  @Value("${local.server.port}")
  int port;

  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;
  @Autowired PlatformTransactionManager manager;
  @Autowired vn.englishconversation.auth.service.AccountService accounts;

  @DynamicPropertySource
  static void isolated(DynamicPropertyRegistry properties) {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:3307/phase[12]_auth_[0-9]{14}\\?connectionTimeZone=UTC"))
      throw new IllegalStateException("Use isolated MySQL test script");
    properties.add("spring.datasource.url", () -> url);
  }

  final ObjectMapper json = new ObjectMapper();

  class Browser {
    final HttpClient client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();

    HttpResponse<String> call(String method, String path, Object body, boolean csrf)
        throws Exception {
      var builder =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/" + path))
              .header("Content-Type", "application/json");
      if (csrf) {
        var token = json.readTree(call("GET", "auth/csrf", null, false).body());
        builder.header(token.get("headerName").asString(), token.get("token").asString());
      }
      return client.send(
          builder
              .method(
                  method,
                  body == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }

    String register() throws Exception {
      String email = "profile-" + UUID.randomUUID() + "@example.test";
      var credentials = Map.of("email", email, "password", "Synthetic profile passphrase");
      var response = call("POST", "auth/register", credentials, true);
      assertEquals(201, response.statusCode());
      assertEquals(200, call("POST", "auth/login", credentials, true).statusCode());
      return json.readTree(response.body()).get("id").asString();
    }

    HttpResponse<String> get(String path) throws Exception {
      return call("GET", path, null, false);
    }
  }

  Map<String, Object> update(String name, String level, List<String> ids) {
    return Map.of(
        "displayName",
        name,
        "selectedLevel",
        level,
        "learningGoal",
        "Giao tiếp khi đi du lịch",
        "dailyGoalMinutes",
        20,
        "topicIds",
        ids);
  }

  @Test
  void signupCreatesProfileAndUpdatesOnlyCurrentOwner() throws Exception {
    Browser one = new Browser(), two = new Browser();
    String id = one.register(), victim = two.register();
    assertEquals("A2", json.readTree(one.get("profile/me").body()).get("selectedLevel").asString());
    String topic = jdbc.queryForObject("select id from topics where code='TRAVEL'", String.class);
    var body = new HashMap<>(update("  Linh  ", "B1", List.of(topic)));
    body.put("userId", victim);
    var saved = one.call("PUT", "profile/me", body, true);
    assertEquals(200, saved.statusCode());
    var value = json.readTree(saved.body());
    assertEquals(id, value.get("userId").asString());
    assertEquals("Linh", value.get("displayName").asString());
    assertEquals("B1", value.get("selectedLevel").asString());
    assertEquals(topic, value.get("topicIds").get(0).asString());
    assertEquals(
        "Người học", json.readTree(two.get("profile/me").body()).get("displayName").asString());
    assertEquals(403, one.call("PUT", "profiles/" + victim, body, true).statusCode());
    assertEquals(403, one.call("PUT", "profile/me", body, false).statusCode());
  }

  @Test
  void validatesProfileAndRejectsUnknownDuplicateOrHiddenTopics() throws Exception {
    Browser b = new Browser();
    b.register();
    String topic = jdbc.queryForObject("select id from topics where code='FOOD'", String.class);
    assertEquals(
        400, b.call("PUT", "profile/me", update("Name", "C1", List.of()), true).statusCode());
    assertEquals(400, b.call("PUT", "profile/me", update(" ", "A2", List.of()), true).statusCode());
    assertEquals(
        400,
        b.call("PUT", "profile/me", update("Name", "A2", List.of(topic, topic)), true)
            .statusCode());
    assertEquals(
        400,
        b.call("PUT", "profile/me", update("Name", "A2", List.of("9223372036854775807")), true)
            .statusCode());
    var bad = new HashMap<>(update("Name", "A2", List.of()));
    bad.put("dailyGoalMinutes", 0);
    assertEquals(400, b.call("PUT", "profile/me", bad, true).statusCode());
    jdbc.update("update topics set is_active=FALSE where id=?", topic);
    try {
      assertEquals(
          400,
          b.call("PUT", "profile/me", update("Name", "A2", List.of(topic)), true).statusCode());
    } finally {
      jdbc.update("update topics set is_active=TRUE where id=?", topic);
    }
    var optional = new HashMap<>(update("Name", "A2", List.of()));
    optional.remove("dailyGoalMinutes");
    optional.remove("learningGoal");
    assertEquals(200, b.call("PUT", "profile/me", optional, true).statusCode());
  }

  @Test
  void catalogHasEightReviewedScenariosAndDoesNotExposeInstructions() throws Exception {
    Browser b = new Browser();
    b.register();
    for (String level : List.of("A2", "B1")) {
      var response = b.get("scenarios?level=" + level + "&size=50");
      assertEquals(200, response.statusCode());
      var page = json.readTree(response.body());
      assertEquals(8, page.get("totalElements").asInt());
      assertEquals(8, page.get("content").size());
      assertFalse(response.body().contains("ai_instruction"));
      assertFalse(response.body().contains("aiInstruction"));
      for (var item : page.get("content")) {
        var detail = b.get("scenarios/" + item.get("id").asString() + "?level=" + level);
        assertEquals(200, detail.statusCode());
        var data = json.readTree(detail.body());
        for (String field :
            List.of("learnerRole", "aiRole", "communicationGoal", "contextDescription"))
          assertFalse(data.get(field).asString().isBlank());
        String internal =
            jdbc.queryForObject(
                "select ai_instruction from scenarios where id=?",
                String.class,
                item.get("id").asString());
        assertFalse(internal.isBlank());
        assertFalse(detail.body().contains(internal));
        assertFalse(data.has("aiInstruction"));
        var roles = data.get("roles");
        assertEquals(2, roles.size());
        assertEquals("DEFAULT", roles.get(0).get("code").asString());
        assertEquals("REVERSE", roles.get(1).get("code").asString());
        assertEquals(data.get("aiRole"), roles.get(1).get("learnerRole"));
        assertFalse(roles.get(1).has("aiInstruction"));
      }
    }
    assertEquals(6, json.readTree(b.get("topics").body()).size());
    String topic = jdbc.queryForObject("select id from topics where code='TRAVEL'", String.class);
    assertEquals(
        2,
        json.readTree(b.get("scenarios?topicId=" + topic + "&level=A2").body())
            .get("totalElements")
            .asInt());
    assertEquals(1, json.readTree(b.get("scenarios?size=1&page=1").body()).get("content").size());
    assertEquals(
        0,
        json.readTree(b.get("scenarios?topicId=9223372036854775807").body()).get("content").size());
    assertEquals(400, b.get("scenarios?level=C1").statusCode());
    assertEquals(400, b.get("scenarios?size=51").statusCode());
    assertEquals(400, b.get("scenarios?page=-1").statusCode());
    assertEquals(400, b.get("scenarios?topicId=invalid").statusCode());
  }

  @Test
  void hiddenDraftArchivedAndUnsupportedLessonsCannotBeSelected() throws Exception {
    Browser b = new Browser();
    b.register();
    String id =
        jdbc.queryForObject(
            "select id from scenarios where code='SELF_INTRODUCTION'", String.class);
    String topic =
        jdbc.queryForObject("select topic_id from scenarios where id=?", String.class, id);
    for (String status : List.of("DRAFT", "ARCHIVED")) {
      jdbc.update("update scenarios set status=? where id=?", status, id);
      try {
        assertEquals(404, b.get("scenarios/" + id).statusCode());
        assertEquals(
            7, json.readTree(b.get("scenarios?size=50").body()).get("totalElements").asInt());
      } finally {
        jdbc.update("update scenarios set status='ACTIVE' where id=?", id);
      }
    }
    jdbc.update("update topics set is_active=FALSE where id=?", topic);
    try {
      assertEquals(404, b.get("scenarios/" + id).statusCode());
      assertEquals(6, json.readTree(b.get("scenarios").body()).get("totalElements").asInt());
      assertEquals(5, json.readTree(b.get("topics").body()).size());
    } finally {
      jdbc.update("update topics set is_active=TRUE where id=?", topic);
    }
    jdbc.update("delete from scenario_levels where scenario_id=? and level='B1'", id);
    try {
      assertEquals(404, b.get("scenarios/" + id + "?level=B1").statusCode());
      assertEquals(200, b.get("scenarios/" + id + "?level=A2").statusCode());
    } finally {
      jdbc.update("insert into scenario_levels(scenario_id,level) values (?,'B1')", id);
    }
  }

  @Test
  void databaseEnforcesLevelsAndInterestForeignKeys() throws Exception {
    Browser browser = new Browser();
    String id = browser.register();
    assertThrows(
        org.springframework.dao.DataAccessException.class,
        () -> jdbc.update("update learner_profiles set selected_level='a2' where user_id=?", id));
    assertThrows(
        org.springframework.dao.DataAccessException.class,
        () ->
            jdbc.update(
                "insert into user_topic_interests(user_id,topic_id,created_at) values"
                    + " (?,9223372036854775807,UTC_TIMESTAMP(6))",
                id));
  }

  @Test
  void anonymousAndLockedAccountsCannotReadOrModifyProfilesOrCatalog() throws Exception {
    Browser b = new Browser();
    assertEquals(401, b.get("profile/me").statusCode());
    assertEquals(401, b.get("topics").statusCode());
    assertEquals(401, b.get("scenarios").statusCode());
    String id = b.register();
    accounts.changeAccess(Long.parseLong(id), "LEARNER", "LOCKED");
    assertEquals(401, b.get("profile/me").statusCode());
    assertEquals(401, b.get("scenarios").statusCode());
  }

  @Test
  void failedOuterTransactionDoesNotLeaveAccountWithoutProfile() {
    String email = "rollback-" + UUID.randomUUID() + "@example.test";
    assertThrows(
        IllegalStateException.class,
        () ->
            new TransactionTemplate(manager)
                .executeWithoutResult(
                    tx -> {
                      accounts.register(email, "Synthetic registration password");
                      throw new IllegalStateException("test rollback");
                    }));
    assertEquals(
        0, jdbc.queryForObject("select count(*) from users where email=?", Integer.class, email));
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "PHASE2_UPGRADE_URL", matches = "jdbc:.*")
  void upgradingV2PreservesAccountsAndBackfillsProfilesWithoutReseedingEdits() {
    String url = System.getenv("PHASE2_UPGRADE_URL");
    assertNotNull(url);
    assertTrue(
        url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:3307/phase2_auth_[0-9]{14}_upgrade\\?connectionTimeZone=UTC"));
    var ds =
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            url,
            System.getenv("SPRING_DATASOURCE_USERNAME"),
            System.getenv("SPRING_DATASOURCE_PASSWORD"));
    var check = new JdbcTemplate(ds);
    org.flywaydb.core.Flyway.configure().dataSource(ds).target("2").load().migrate();
    String hash = encoder.encode("Synthetic upgrade password");
    check.update(
        "insert into users(email,password_hash,role,status,security_version,created_at,updated_at)"
            + " values (?,?,'LEARNER','ACTIVE',0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        "upgrade@example.test",
        hash);
    org.flywaydb.core.Flyway.configure().dataSource(ds).target("7").load().migrate();
    check.update("""
        INSERT INTO practice_sessions(id,user_id,scenario_id,client_request_id,request_fingerprint,
          selected_level,scenario_snapshot,status,started_at,last_activity_at)
        SELECT 1,u.id,s.id,'upgrade-practice',REPEAT('a',64),'A2',JSON_OBJECT(),'ACTIVE',
          UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
        FROM users u CROSS JOIN scenarios s WHERE u.email='upgrade@example.test'
          AND s.code='SELF_INTRODUCTION'
        """);
    check.update("""
        INSERT INTO ai_requests(id,user_id,session_id,task_type,operation_key,request_fingerprint,
          status,prompt_version,created_at)
        SELECT 1,user_id,id,'CHAT','upgrade-turn',REPEAT('a',64),'SUCCEEDED','fixture',UTC_TIMESTAMP(6)
        FROM practice_sessions WHERE id=1
        """);
    check.update("""
        INSERT INTO conversation_turns(id,session_id,turn_no,client_request_id,request_fingerprint,
          kind,status,ai_request_id,created_at)
        VALUES (1,1,1,'upgrade-message',REPEAT('a',64),'LEARNER_TEXT','SUCCEEDED',1,UTC_TIMESTAMP(6))
        """);
    check.update("""
        INSERT INTO messages(id,session_id,turn_id,sender,content,input_type,created_at)
        VALUES (1,1,1,'LEARNER','An existing message.','TEXT',UTC_TIMESTAMP(6))
        """);
    var flyway = org.flywaydb.core.Flyway.configure().dataSource(ds).load();
    flyway.migrate();
    assertNull(check.queryForObject("SELECT deletion_requested_at FROM users WHERE email='upgrade@example.test'",java.sql.Timestamp.class));

    assertEquals("An existing message.", check.queryForObject(
        "SELECT content FROM messages WHERE id=1 AND input_type='TEXT' AND audio_submission_id IS NULL",
        String.class));
    assertEquals("TEXT/TEXT", check.queryForObject(
        "SELECT CONCAT(mode,'/',initial_mode) FROM practice_sessions WHERE id=1", String.class));
    assertEquals("DEFAULT", check.queryForObject(
        "SELECT role_code FROM practice_sessions WHERE id=1", String.class));
    assertEquals(8, check.queryForObject("SELECT count(*) FROM scenario_role_variants", Integer.class));
    assertEquals(
        hash,
        check.queryForObject(
            "select password_hash from users where email='upgrade@example.test'", String.class));
    assertEquals(1, check.queryForObject("select count(*) from learner_profiles", Integer.class));
    assertEquals(8, check.queryForObject("select count(*) from scenarios", Integer.class));
    check.update(
        "update scenarios set title='Edited catalog title' where code='SELF_INTRODUCTION'");
    flyway.migrate();
    assertEquals(
        "Edited catalog title",
        check.queryForObject(
            "select title from scenarios where code='SELF_INTRODUCTION'", String.class));
    assertEquals(8, check.queryForObject("select count(*) from scenarios", Integer.class));
  }
}
