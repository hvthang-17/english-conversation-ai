package vn.englishconversation.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.*;
import vn.englishconversation.auth.bootstrap.AdminBootstrap;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.user.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "PHASE1_MYSQL_TEST", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SessionMysqlTest {
  @Value("${local.server.port}")
  int port;

  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired AccountService accounts;
  @Autowired PlatformTransactionManager manager;
  @Autowired org.springframework.session.jdbc.JdbcIndexedSessionRepository sessionRepository;

  @org.springframework.test.context.DynamicPropertySource
  static void isolatedDatabase(
      org.springframework.test.context.DynamicPropertyRegistry properties) {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:3307/phase[12]_auth_[0-9]{14}\\?connectionTimeZone=UTC"))
      throw new IllegalStateException(
          "Use scripts/phase1/test-mysql.ps1 with a new isolated MySQL database");
    properties.add("spring.datasource.url", () -> url);
  }

  final ObjectMapper json = new ObjectMapper();
  static final String PASSWORD = "  A long test passphrase  ";

  class Browser {
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

    HttpResponse<String> request(String method, String path, String body, boolean csrf)
        throws Exception {
      var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/" + path));
      if (csrf) {
        var token = get("auth/csrf");
        var value = json.readTree(token.body());
        b.header(value.get("headerName").asString(), value.get("token").asString());
      }
      b.header("Content-Type", "application/json");
      return client.send(
          b.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body)).build(),
          HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String path) throws Exception {
      return request("GET", path, null, false);
    }

    HttpResponse<String> post(String path, Object body) throws Exception {
      return request("POST", path, json.writeValueAsString(body), true);
    }

    String cookie() {
      return cookies.getCookieStore().getCookies().stream()
          .filter(c -> c.getName().equals("SESSION"))
          .findFirst()
          .orElseThrow()
          .getValue();
    }
  }

  String email() {
    return "test-" + UUID.randomUUID() + "@example.test";
  }

  String register(Browser b, String email) throws Exception {
    var r = b.post("auth/register", Map.of("email", email, "password", PASSWORD));
    assertEquals(201, r.statusCode());
    return json.readTree(r.body()).get("id").asString();
  }

  void login(Browser b, String email) throws Exception {
    assertEquals(
        200, b.post("auth/login", Map.of("email", email, "password", PASSWORD)).statusCode());
  }

  String sessionId(Browser b) {
    return new String(
        Base64.getDecoder().decode(b.cookie()), java.nio.charset.StandardCharsets.UTF_8);
  }

  @Test
  @Order(0)
  void bootstrapCreatesOnlyFirstAdmin() throws Exception {
    assertFalse(users.existsByRole("ADMIN"));
    String email = email();
    Browser existing = new Browser();
    String learnerEmail = email();
    register(existing, learnerEmail);
    assertThrows(
        IllegalStateException.class,
        () ->
            new AdminBootstrap(true, learnerEmail, PASSWORD, users, encoder, jdbc, manager)
                .run(new DefaultApplicationArguments()));
    assertEquals("LEARNER", users.findByEmail(learnerEmail).orElseThrow().role);
    new AdminBootstrap(true, email, PASSWORD, users, encoder, jdbc, manager)
        .run(new DefaultApplicationArguments());
    var admin = users.findByEmail(email).orElseThrow();
    assertEquals("ADMIN", admin.role);
    assertTrue(encoder.matches(PASSWORD, admin.passwordHash));
    new AdminBootstrap(true, email(), "Another bootstrap password", users, encoder, jdbc, manager)
        .run(new DefaultApplicationArguments());
    assertEquals(1, users.findAll().stream().filter(u -> u.role.equals("ADMIN")).count());
  }

  @Test
  void migrationAndDatabaseEmailRules() throws Exception {
    assertEquals(
        10,
        jdbc.queryForObject(
            "select count(*) from flyway_schema_history where success=1 and type='SQL'",
            Integer.class));
    Browser b = new Browser();
    String email = email();
    String id = register(b, "  " + email.toUpperCase(Locale.ROOT) + "  ");
    assertEquals(
        409, b.post("auth/register", Map.of("email", email, "password", PASSWORD)).statusCode());
    assertEquals(email, users.findById(Long.valueOf(id)).orElseThrow().email);
    var failure =
        assertThrows(
            org.springframework.dao.DataAccessException.class,
            () ->
                jdbc.update(
                    "update users set email=? where id=?", email.toUpperCase(Locale.ROOT), id));
    assertEquals(3819, ((java.sql.SQLException) failure.getCause()).getErrorCode());
    var u = users.findById(Long.valueOf(id)).orElseThrow();
    assertNotEquals(PASSWORD, u.passwordHash);
    assertTrue(encoder.matches(PASSWORD, u.passwordHash));
    assertFalse(encoder.matches(PASSWORD.strip(), u.passwordHash));
  }

  @Test
  void rotationPersistenceMultipleDevicesLogoutAndCsrf() throws Exception {
    Browser a = new Browser(), b = new Browser();
    String email = email();
    String id = register(a, email);
    String anonymous = a.cookie();
    assertEquals(
        403,
        a.request(
                "POST",
                "auth/login",
                json.writeValueAsString(Map.of("email", email, "password", PASSWORD)),
                false)
            .statusCode());
    var badToken =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
            .header("X-CSRF-TOKEN", "wrong")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();
    assertEquals(403, a.client.send(badToken, HttpResponse.BodyHandlers.ofString()).statusCode());
    login(a, email);
    assertNotEquals(anonymous, a.cookie());
    assertEquals(200, a.get("auth/me").statusCode());
    assertEquals(
        id,
        jdbc.queryForObject(
            "select PRINCIPAL_NAME from SPRING_SESSION where SESSION_ID=?",
            String.class,
            sessionId(a)));
    assertEquals(
        1800,
        jdbc.queryForObject(
            "select MAX_INACTIVE_INTERVAL from SPRING_SESSION where SESSION_ID=?",
            Integer.class,
            sessionId(a)));
    assertTrue(
        jdbc.queryForObject(
                "select count(*) from SPRING_SESSION_ATTRIBUTES where SESSION_PRIMARY_ID=(select"
                    + " PRIMARY_ID from SPRING_SESSION where SESSION_ID=?) and"
                    + " ATTRIBUTE_NAME='SPRING_SECURITY_CONTEXT'",
                Integer.class,
                sessionId(a))
            > 0);
    org.springframework.session.Session persisted = sessionRepository.findById(sessionId(a));
    org.springframework.security.core.context.SecurityContext context =
        persisted.getAttribute("SPRING_SECURITY_CONTEXT");
    assertInstanceOf(AccountPrincipal.class, context.getAuthentication().getPrincipal());
    assertNull(context.getAuthentication().getCredentials());
    login(b, email);
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(*) from SPRING_SESSION where PRINCIPAL_NAME=?", Integer.class, id));
    assertEquals(403, a.request("POST", "auth/logout", "{}", false).statusCode());
    var out = a.post("auth/logout", Map.of());
    assertEquals(204, out.statusCode());
    assertTrue(
        out.headers().allValues("set-cookie").stream().anyMatch(s -> s.contains("Max-Age=0")));
    assertEquals(401, a.get("auth/me").statusCode());
    assertEquals(200, b.get("auth/me").statusCode());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from SPRING_SESSION_ATTRIBUTES a left join SPRING_SESSION s on"
                + " a.SESSION_PRIMARY_ID=s.PRIMARY_ID where s.PRIMARY_ID is null",
            Integer.class));
  }

  @Test
  void oldCsrfTokenCannotBeReusedAfterLogin() throws Exception {
    Browser b = new Browser();
    String email = email();
    register(b, email);
    var old = json.readTree(b.get("auth/csrf").body());
    login(b, email);
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/logout"))
            .header(old.get("headerName").asString(), old.get("token").asString())
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    assertEquals(403, b.client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    assertEquals(200, b.get("auth/me").statusCode());
    assertEquals(204, b.post("auth/logout", Map.of()).statusCode());
  }

  @Test
  void concurrentDuplicateRegistrationHasOneWinner() throws Exception {
    String email = email();
    Browser a = new Browser(), b = new Browser();
    a.get("auth/csrf");
    b.get("auth/csrf");
    try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var one =
          pool.submit(
              () ->
                  a.post("auth/register", Map.of("email", email, "password", PASSWORD))
                      .statusCode());
      var two =
          pool.submit(
              () ->
                  b.post(
                          "auth/register",
                          Map.of("email", email.toUpperCase(Locale.ROOT), "password", PASSWORD))
                      .statusCode());
      var codes = new java.util.ArrayList<>(List.of(one.get(), two.get()));
      java.util.Collections.sort(codes);
      assertEquals(List.of(201, 409), codes);
    }
    assertEquals(
        1, jdbc.queryForObject("select count(*) from users where email=?", Integer.class, email));
  }

  @Test
  void adminSelfDemotionEndsOwnSession() throws Exception {
    Browser admin = new Browser();
    String email = email();
    String id = register(admin, email);
    accounts.changeAccess(Long.parseLong(id), "ADMIN", "ACTIVE");
    login(admin, email);
    assertEquals(
        200,
        admin
            .request(
                "PATCH",
                "admin/users/" + id + "/access",
                "{\"role\":\"LEARNER\",\"status\":\"ACTIVE\"}",
                true)
            .statusCode());
    assertEquals(401, admin.get("auth/me").statusCode());
    login(admin, email);
    assertEquals(
        403,
        admin
            .request(
                "PATCH",
                "admin/users/" + id + "/access",
                "{\"role\":\"ADMIN\",\"status\":\"ACTIVE\"}",
                true)
            .statusCode());
  }

  @Test
  @SuppressWarnings("deprecation")
  void readsSessionPrincipalWrittenBeforePackageRefactor() throws Exception {
    Browser browser = new Browser();
    String email = email();
    String id = register(browser, email);
    login(browser, email);
    var account = users.findById(Long.parseLong(id)).orElseThrow();
    var legacy =
        new vn.englishconversation.auth.AccountPrincipal(account.id, account.securityVersion);
    var authentication =
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken
            .authenticated(
                legacy,
                null,
                List.of(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_LEARNER")));
    var context = new org.springframework.security.core.context.SecurityContextImpl(authentication);
    var bytes = new java.io.ByteArrayOutputStream();
    try (var output = new java.io.ObjectOutputStream(bytes)) {
      output.writeObject(context);
    }
    assertEquals(
        1,
        jdbc.update(
            "update SPRING_SESSION_ATTRIBUTES set ATTRIBUTE_BYTES=? where"
                + " ATTRIBUTE_NAME='SPRING_SECURITY_CONTEXT' and SESSION_PRIMARY_ID=(select"
                + " PRIMARY_ID from SPRING_SESSION where SESSION_ID=?)",
            bytes.toByteArray(),
            sessionId(browser)));
    var response = browser.get("auth/me");
    assertEquals(200, response.statusCode());
    assertEquals(id, json.readTree(response.body()).get("id").asString());
    org.springframework.session.Session persisted = sessionRepository.findById(sessionId(browser));
    org.springframework.security.core.context.SecurityContext restored =
        persisted.getAttribute("SPRING_SECURITY_CONTEXT");
    assertInstanceOf(AccountPrincipal.class, restored.getAuthentication().getPrincipal());
  }

  @Test
  void passwordRevokesAllDevicesAndRequiresCurrentPassword() throws Exception {
    Browser a = new Browser(), b = new Browser();
    String email = email();
    String id = register(a, email);
    login(a, email);
    login(b, email);
    assertEquals(
        400,
        a.post(
                "auth/password",
                Map.of("currentPassword", "incorrect", "newPassword", "Next password with spaces "))
            .statusCode());
    assertEquals(200, b.get("auth/me").statusCode());
    assertEquals(
        204,
        a.post(
                "auth/password",
                Map.of("currentPassword", PASSWORD, "newPassword", "Next password with spaces "))
            .statusCode());
    assertEquals(401, a.get("auth/me").statusCode());
    assertEquals(401, b.get("auth/me").statusCode());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from SPRING_SESSION where PRINCIPAL_NAME=?", Integer.class, id));
    assertEquals(
        401, a.post("auth/login", Map.of("email", email, "password", PASSWORD)).statusCode());
    assertEquals(
        200,
        a.post("auth/login", Map.of("email", email, "password", "Next password with spaces "))
            .statusCode());
  }

  @Test
  void roleStatusRevocationAndStalePrincipal() throws Exception {
    Browser a = new Browser(), admin = new Browser();
    String email = email();
    String id = register(a, email);
    login(a, email);
    assertEquals(
        403,
        a.request(
                "PATCH",
                "admin/users/" + id + "/access",
                "{\"role\":\"ADMIN\",\"status\":\"ACTIVE\"}",
                true)
            .statusCode());
    String adminEmail = email();
    String aid = register(admin, adminEmail);
    accounts.changeAccess(Long.parseLong(aid), "ADMIN", "ACTIVE");
    login(admin, adminEmail);
    assertEquals(
        200,
        admin
            .request(
                "PATCH",
                "admin/users/" + id + "/access",
                "{\"role\":\"LEARNER\",\"status\":\"LOCKED\"}",
                true)
            .statusCode());
    assertEquals(401, a.get("auth/me").statusCode());
    assertEquals(
        401, a.post("auth/login", Map.of("email", email, "password", PASSWORD)).statusCode());
    accounts.changeAccess(Long.parseLong(id), "LEARNER", "ACTIVE");
    login(a, email);
    // Simulate an old session missed by deletion, including a future concurrent save.
    jdbc.update("update users set security_version=security_version+1 where id=?", id);
    assertEquals(401, a.get("auth/me").statusCode());
    login(a, email);
    accounts.changeAccess(Long.parseLong(id), "ADMIN", "ACTIVE");
    assertEquals(401, a.get("auth/me").statusCode());
    login(a, email);
    jdbc.update("update users set role='LEARNER' where id=?", id);
    assertEquals(401, a.get("auth/me").statusCode());
    login(a, email);
    jdbc.update("update users set status='LOCKED' where id=?", id);
    assertEquals(401, a.get("auth/me").statusCode());
  }

  @Test
  void expiryAndCookieAttributes() throws Exception {
    Browser b = new Browser();
    String email = email();
    register(b, email);
    var r = b.get("auth/csrf");
    login(b, email);
    String sid = sessionId(b);
    jdbc.update(
        "update SPRING_SESSION set LAST_ACCESS_TIME=?, EXPIRY_TIME=? where SESSION_ID=?",
        System.currentTimeMillis() - 1801000,
        System.currentTimeMillis() - 1000,
        sid);
    assertEquals(401, b.get("auth/me").statusCode());
    Browser fresh = new Browser();
    var csrf = fresh.get("auth/csrf");
    String cookie = csrf.headers().firstValue("set-cookie").orElseThrow();
    assertTrue(cookie.contains("HttpOnly"));
    assertTrue(cookie.contains("SameSite=Lax"));
    assertTrue(cookie.contains("Path=/"));
    assertFalse(cookie.contains("Secure"));
  }

  @Test
  void bootstrapDoesNotResetExistingAdmin() throws Exception {
    // Bootstrap on this test database may already contain admins from other tests.
    String email = email();
    Browser b = new Browser();
    String id = register(b, email);
    accounts.changeAccess(Long.parseLong(id), "ADMIN", "ACTIVE");
    var before = users.findById(Long.parseLong(id)).orElseThrow().passwordHash;
    new AdminBootstrap(true, email, "A different bootstrap password", users, encoder, jdbc, manager)
        .run(new DefaultApplicationArguments());
    assertEquals(before, users.findById(Long.parseLong(id)).orElseThrow().passwordHash);
  }

  @Test
  void expiredSessionCleanupDeletesAttributes() throws Exception {
    Browser b = new Browser();
    String email = email();
    String id = register(b, email);
    login(b, email);
    String sid = sessionId(b);
    jdbc.update(
        "update SPRING_SESSION set EXPIRY_TIME=? where SESSION_ID=?",
        System.currentTimeMillis() - 1000,
        sid);
    assertTrue(
        jdbc.queryForObject(
                "select count(*) from SPRING_SESSION_ATTRIBUTES where SESSION_PRIMARY_ID=(select"
                    + " PRIMARY_ID from SPRING_SESSION where SESSION_ID=?)",
                Integer.class,
                sid)
            > 0);
    sessionRepository.cleanUpExpiredSessions();
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from SPRING_SESSION where PRINCIPAL_NAME=?", Integer.class, id));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from SPRING_SESSION_ATTRIBUTES a left join SPRING_SESSION s on"
                + " a.SESSION_PRIMARY_ID=s.PRIMARY_ID where s.PRIMARY_ID is null",
            Integer.class));
  }

  @Test
  void signupCannotChooseAdminAndValidationIsBounded() throws Exception {
    Browser b = new Browser();
    String email = email();
    var r = b.post("auth/register", Map.of("email", email, "password", PASSWORD, "role", "ADMIN"));
    assertEquals(201, r.statusCode());
    assertEquals("LEARNER", json.readTree(r.body()).get("role").asString());
    assertEquals(
        400, b.post("auth/register", Map.of("email", email(), "password", "short")).statusCode());
    assertEquals(
        400,
        b.post("auth/register", Map.of("email", "bad email", "password", PASSWORD)).statusCode());
    assertEquals(
        400,
        b.post("auth/register", Map.of("email", email(), "password", "x".repeat(129)))
            .statusCode());
  }
}
