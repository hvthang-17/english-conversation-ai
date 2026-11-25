package vn.englishconversation.conversation;

import vn.englishconversation.admin.dto.AdminCatalog.ScenarioInput;
import vn.englishconversation.admin.dto.AdminCatalog.ScenarioVariant;
import vn.englishconversation.auth.service.AccountLifecycleService;
import vn.englishconversation.auth.job.AccountDeletionWorker;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.englishconversation.ai.provider.TextProvider;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.conversation.dto.*;
import vn.englishconversation.conversation.service.PracticeService;
import vn.englishconversation.voice.service.VoiceStorage;
import vn.englishconversation.voice.service.WaveAudio;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "CHAT_GLOBAL_RPM=60",
      "CHAT_USER_RPM=60",
      "CHAT_GLOBAL_CONCURRENT=1",
      "CHAT_FINGERPRINT_SECRET=synthetic-test-only-secret-1234567890",
      "CHAT_TOTAL_ATTEMPT_LIMIT=0",
      "GEMINI_CHAT_ENABLED=false"
    })
@EnabledIfEnvironmentVariable(named = "PHASE1_MYSQL_TEST", matches = "true")
class PracticeMysqlTest {
  @org.springframework.test.context.bean.override.mockito.MockitoBean
  vn.englishconversation.pronunciation.service.PronunciationProvider pronunciationProvider;
  @Autowired PracticeService service;
  @Autowired AccountService accounts;
  @Autowired JdbcTemplate jdbc;
  @Autowired FixtureProvider provider;
  @Autowired AccountLifecycleService lifecycle;
  @Autowired AccountDeletionWorker deletion;
  @Autowired VoiceStorage voiceStorage;

  @org.springframework.beans.factory.annotation.Value("${local.server.port}")
  int port;

  @Autowired vn.englishconversation.learning.service.LearningService learning;

  @BeforeEach
  void ageAttemptsFromPreviousTestsOutOfTheQuotaWindow() {
    jdbc.update("""
        UPDATE ai_attempts
        SET started_at=started_at-INTERVAL 61 SECOND,finished_at=finished_at-INTERVAL 61 SECOND
        WHERE status<>'RUNNING' AND started_at>=UTC_TIMESTAMP(6)-INTERVAL 60 SECOND
        """);
  }

  @Test
  void adminManagesCatalogAndAccountsWithAuditAndPreservesPractice() throws Exception {
    var admin = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var learner = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var json = new tools.jackson.databind.ObjectMapper();
    String base = "http://127.0.0.1:" + port + "/api/v1/";
    String code = "QA_" + java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
    String email = code.toLowerCase(java.util.Locale.ROOT) + "@example.test";
    String password = "Synthetic admin test password";
    var person = accounts.register(email, password);
    var adminAccount = accounts.register("admin-" + email, password);
    long actor = Long.parseLong(adminAccount.id());
    accounts.changeAccess(actor, "ADMIN", "ACTIVE");
    assertEquals(200, post(admin, json, base, "auth/login",
        java.util.Map.of("email", adminAccount.email(), "password", password), true).statusCode());
    assertEquals(200, post(learner, json, base, "auth/login",
        java.util.Map.of("email", email, "password", password), true).statusCode());
    assertEquals(403, request(learner, json, base, "admin/users", "GET", null, false).statusCode());
    assertEquals(403, request(learner, json, base, "admin/overview", "GET", null, false).statusCode());
    var search = request(admin, json, base, "admin/users?email=" + email, "GET", null, false);
    assertEquals(200, search.statusCode(), search.body());
    var matches = json.readTree(search.body()).get("items");
    assertEquals(2, matches.size());
    assertTrue(java.util.stream.StreamSupport.stream(matches.spliterator(), false)
        .anyMatch(row -> person.id().equals(row.get("id").asString()) && email.equals(row.get("email").asString())));
    assertFalse(matches.get(0).has("passwordHash"));
    assertEquals(1, json.readTree(request(admin, json, base,
        "admin/users?email=" + email + "&page=1&size=1", "GET", null, false).body()).get("items").size());
    for (var change : List.of(java.util.Map.of("role", "LEARNER"), java.util.Map.of("status", "LOCKED"))) {
      var denied = request(admin, json, base, "admin/users/" + actor + "/access", "PATCH", change, true);
      assertEquals(409, denied.statusCode());
      assertEquals("ADMIN_SELF_ACCESS", json.readTree(denied.body()).get("code").asString());
    }

    var topic = new vn.englishconversation.admin.dto.AdminCatalog.TopicInput(code, "Fixture topic", null, true, 3);
    assertEquals(403, post(learner, json, base, "admin/topics", topic, true).statusCode());
    assertEquals(403, post(admin, json, base, "admin/topics", topic, false).statusCode());
    var created = post(admin, json, base, "admin/topics", topic, true);
    assertEquals(201, created.statusCode(), created.body());
    String topicId = json.readTree(created.body()).get("id").asString();
    assertEquals(409, post(admin, json, base, "admin/topics", topic, true).statusCode());
    String catalog = "admin/topics/" + topicId + "/scenarios";
    assertEquals(422, post(admin, json, base, catalog,
        adminScenario(code, "Fixture lesson", "ACTIVE", List.of()), true).statusCode());
    var initial = new ScenarioVariant("REVERSE", "Waiter", "Customer", "Confirm the order.",
        "At the cafe counter.", "Act as the original cafe customer.");
    var duplicatePair = new ScenarioVariant("COPY", " waiter ", "CUSTOMER",
        initial.communicationGoal(), initial.contextDescription(), initial.aiInstruction());
    var reserved = new ScenarioVariant("DEFAULT", initial.learnerRole(), initial.aiRole(),
        initial.communicationGoal(), initial.contextDescription(), initial.aiInstruction());
    var invalidPrompt = new ScenarioVariant("INVALID", "Cook", "Courier", "Check the order.",
        "In the kitchen.", "Invalid control " + (char) 1);
    for (var invalid : List.of(List.of(initial, initial), List.of(initial, duplicatePair),
        List.of(reserved), List.of(invalidPrompt))) {
      assertEquals(422, post(admin, json, base, catalog,
          adminScenario(code, "Fixture lesson", "ACTIVE", List.of("A2", "B1"), invalid), true).statusCode());
    }
    var scenario = post(admin, json, base, catalog,
        adminScenario(code, "Fixture lesson", "ACTIVE", List.of("A2", "B1"), List.of(initial)), true);
    assertEquals(201, scenario.statusCode(), scenario.body());
    String scenarioId = json.readTree(scenario.body()).get("id").asString();
    assertEquals(403, request(learner, json, base, catalog, "GET", null, false).statusCode());
    var publicLesson = request(learner, json, base, "scenarios/" + scenarioId + "?level=A2", "GET", null, false);
    assertEquals(200, publicLesson.statusCode(), publicLesson.body());
    assertFalse(publicLesson.body().contains("aiInstruction"));
    assertFalse(publicLesson.body().contains(initial.aiInstruction()));
    assertTrue(publicLesson.body().contains("REVERSE"));
    var rolePath = catalog + "/" + scenarioId + "/roles";
    assertEquals(403, request(learner, json, base, rolePath, "GET", null, false).statusCode());
    var adminRoles = request(admin, json, base, rolePath, "GET", null, false);
    assertEquals(200, adminRoles.statusCode());
    assertEquals(initial.aiInstruction(), json.readTree(adminRoles.body()).get(0).get("aiInstruction").asString());
    var practice = post(learner, json, base, "practice-sessions",
        new CreatePracticeRequest(scenarioId, "A2", java.util.UUID.randomUUID().toString(), "TEXT", "REVERSE"), true);
    assertTrue(provider.lastPrompt.instruction().contains(initial.aiInstruction()));
    assertEquals(200, practice.statusCode(), practice.body());
    String practiceId = json.readTree(practice.body()).get("id").asString();
    assertEquals(404, request(admin, json, base, "practice-sessions/" + practiceId, "GET", null, false).statusCode());

    var next = new ScenarioVariant("REVERSE", "Barista", "Guest", "Confirm a coffee order.",
        "At a coffee bar.", "Act as the updated coffee guest.");
    var update = request(admin, json, base, catalog + "/" + scenarioId, "PUT",
        adminScenario(code, "Renamed lesson", "ACTIVE", List.of("B1"), List.of(next)), true);
    assertEquals(200, update.statusCode(), update.body());
    assertEquals(404, request(learner, json, base, "scenarios/" + scenarioId + "?level=A2", "GET", null, false).statusCode());
    var updatedPublic = request(learner, json, base, "scenarios/" + scenarioId + "?level=B1", "GET", null, false);
    assertEquals(200, updatedPublic.statusCode(), updatedPublic.body());
    assertTrue(updatedPublic.body().contains("Barista"));
    assertFalse(updatedPublic.body().contains(next.aiInstruction()));
    service.send(new AccountPrincipal(Long.parseLong(person.id()), 0), Long.parseLong(practiceId),
        new SendTextRequest("What would you like to order?", "admin-snapshot-turn"));
    assertTrue(provider.lastPrompt.instruction().contains(initial.aiInstruction()));
    assertFalse(provider.lastPrompt.instruction().contains(next.aiInstruction()));
    String topicPath = "admin/topics/" + topicId;
    assertEquals(200, request(admin, json, base, topicPath, "PUT",
        new vn.englishconversation.admin.dto.AdminCatalog.TopicInput(code, "Hidden topic", null, false, 3), true).statusCode());
    assertEquals(404, request(learner, json, base, "scenarios/" + scenarioId + "?level=B1", "GET", null, false).statusCode());
    assertEquals(422, request(admin, json, base, catalog + "/" + scenarioId, "PUT",
        adminScenario(code, "Renamed lesson", "ACTIVE", List.of("B1")), true).statusCode());
    assertEquals(200, request(admin, json, base, catalog + "/" + scenarioId, "PUT",
        adminScenario(code, "Renamed lesson", "ARCHIVED", List.of(), List.of()), true).statusCode());
    assertEquals(0, json.readTree(request(admin, json, base, rolePath, "GET", null, false).body()).size());
    var reopened = request(learner, json, base, "practice-sessions/" + practiceId, "GET", null, false);
    assertEquals("Fixture lesson", json.readTree(reopened.body()).get("title").asString());
    assertEquals("A2", json.readTree(reopened.body()).get("selectedLevel").asString());
    assertEquals(initial.learnerRole(), json.readTree(reopened.body()).get("learnerRole").asString());
    var history = request(learner, json, base, "learning/history", "GET", null, false);
    assertEquals("Fixture lesson", json.readTree(history.body()).get("items").get(0).get("title").asString());

    String access = "admin/users/" + person.id() + "/access";
    assertEquals(200, request(admin, json, base, access, "PATCH", java.util.Map.of("status", "LOCKED"), true).statusCode());
    assertEquals(401, request(learner, json, base, "auth/me", "GET", null, false).statusCode());
    assertEquals(200, request(admin, json, base, access, "PATCH", java.util.Map.of("status", "ACTIVE"), true).statusCode());
    assertEquals(401, request(learner, json, base, "auth/me", "GET", null, false).statusCode());
    assertEquals(200, post(learner, json, base, "auth/login",
        java.util.Map.of("email", email, "password", password), true).statusCode());
    assertEquals(List.of("TOPIC_CREATED", "SCENARIO_CREATED", "SCENARIO_UPDATED", "TOPIC_UPDATED",
        "SCENARIO_UPDATED", "USER_ACCESS_UPDATED", "USER_ACCESS_UPDATED"),
        jdbc.queryForList("SELECT action FROM admin_audit_logs WHERE actor_user_id=? ORDER BY id",
            String.class, actor));
    assertEquals(List.of("TOPIC:" + topicId, "SCENARIO:" + scenarioId, "SCENARIO:" + scenarioId,
        "TOPIC:" + topicId, "SCENARIO:" + scenarioId),
        jdbc.queryForList("""
            SELECT CONCAT(target_type,':',target_id) FROM admin_audit_logs
            WHERE actor_user_id=? AND target_type IN ('TOPIC','SCENARIO') ORDER BY id
            """, String.class, actor));
    assertEquals(2, jdbc.queryForObject("""
        SELECT COUNT(*) FROM admin_audit_logs WHERE actor_user_id=? AND target_type='USER'
          AND target_id=? AND JSON_EXTRACT(safe_metadata,'$.role')='LEARNER'
        """, Integer.class, actor, Long.parseLong(person.id())));
    assertEquals(0, jdbc.queryForObject("""
        SELECT COUNT(*) FROM admin_audit_logs
        WHERE actor_user_id=? AND CAST(safe_metadata AS CHAR) LIKE '%@example.test%'
        """, Integer.class, actor));
    var overview = request(admin, json, base, "admin/overview", "GET", null, false);
    assertEquals(200, overview.statusCode(), overview.body());
    assertTrue(json.readTree(overview.body()).get("aiAttempts24h").asLong() > 0);
    var promoted = request(admin, json, base, access, "PATCH", java.util.Map.of("role", "ADMIN"), true);
    assertEquals(200, promoted.statusCode(), promoted.body());
    assertEquals("ACTIVE", json.readTree(promoted.body()).get("status").asString());
    assertEquals(401, request(learner, json, base, "auth/me", "GET", null, false).statusCode());
    assertEquals(200, post(learner, json, base, "auth/login",
        java.util.Map.of("email", email, "password", password), true).statusCode());
    assertEquals(200, request(learner, json, base, "admin/users", "GET", null, false).statusCode());
    assertEquals(409, request(admin, json, base, catalog + "/" + scenarioId, "DELETE", null, true).statusCode());
    var disposable = post(admin, json, base, catalog, adminScenario(code + "_DRAFT", "Disposable", "DRAFT", List.of("A2")), true);
    assertEquals(201, disposable.statusCode(), disposable.body());
    String draftPath = catalog + "/" + json.readTree(disposable.body()).get("id").asString();
    assertEquals(403, request(admin, json, base, draftPath, "DELETE", null, false).statusCode());
    assertEquals(204, request(admin, json, base, draftPath, "DELETE", null, true).statusCode());
    assertEquals(404, request(admin, json, base, draftPath + "/roles", "GET", null, false).statusCode());
    String archivedPath = catalog + "/" + scenarioId;
    assertEquals(422, post(admin, json, base, archivedPath + "/restore", null, true).statusCode());
    assertEquals(200, request(admin, json, base, topicPath, "PUT", topic, true).statusCode());
    assertEquals(422, post(admin, json, base, archivedPath + "/restore", null, true).statusCode());
    assertEquals(200, request(admin, json, base, archivedPath, "PUT",
        adminScenario(code, "Renamed lesson", "ARCHIVED", List.of("B1")), true).statusCode());
    assertEquals(403, post(admin, json, base, archivedPath + "/restore", null, false).statusCode());
    assertEquals(204, post(admin, json, base, archivedPath + "/restore", null, true).statusCode());
    assertEquals(200, request(learner, json, base, "scenarios/" + scenarioId + "?level=B1", "GET", null, false).statusCode());
  }

  @Test
  void materialAdminProtectsDraftsAndPreservesListeningHistory() throws Exception {
    var admin = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var learner = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var json = new tools.jackson.databind.ObjectMapper();
    String base = "http://127.0.0.1:" + port + "/api/v1/";
    String suffix = java.util.UUID.randomUUID().toString();
    String password = "Synthetic material test password";
    var author = accounts.register("editor-" + suffix + "@example.test", password);
    var student = accounts.register("student-" + suffix + "@example.test", password);
    accounts.changeAccess(Long.parseLong(author.id()), "ADMIN", "ACTIVE");
    assertEquals(200, post(admin, json, base, "auth/login",
        java.util.Map.of("email", author.email(), "password", password), true).statusCode());
    assertEquals(200, post(learner, json, base, "auth/login",
        java.util.Map.of("email", student.email(), "password", password), true).statusCode());
    String topics = "admin/materials/TOPICS";
    var draft = java.util.Map.of("code", "DRAFT_" + suffix.substring(0, 8).toUpperCase(java.util.Locale.ROOT),
        "name", "Chủ đề thử nghiệm", "sort_order", "0");
    assertEquals(403, post(learner, json, base, topics, draft, true).statusCode());
    assertEquals(403, post(admin, json, base, topics, draft, false).statusCode());
    var created = post(admin, json, base, topics, draft, true);
    assertEquals(201, created.statusCode(), created.body());
    String topic = topics + "/" + json.readTree(created.body()).get("id").asString();
    assertEquals(204, request(admin, json, base, topic, "DELETE", null, true).statusCode());
    assertEquals(404, request(admin, json, base, topic, "DELETE", null, true).statusCode());
    assertEquals(422, post(admin, json, base, topics, java.util.Map.of("sort_order", 0), true).statusCode());

    String words = "admin/materials/WORDS";
    var word = java.util.Map.of("lesson_id", "101", "sort_order", "20", "kind", "WORD",
        "expression", "hello", "meaning_vi", "xin chào", "example_en", "Hello, Sam!", "example_vi", "Xin chào, Sam!");
    var wordDraft = post(admin, json, base, words, word, true);
    assertEquals(201, wordDraft.statusCode(), wordDraft.body());
    String wordPath = words + "/" + json.readTree(wordDraft.body()).get("id").asString();
    assertEquals(422, post(admin, json, base, wordPath + "/publish", null, true).statusCode());
    var corrected = new java.util.HashMap<>(word);
    corrected.put("ipa", "/həˈloʊ/");
    assertEquals(200, request(admin, json, base, wordPath, "PUT", corrected, true).statusCode());
    assertEquals(204, post(admin, json, base, wordPath + "/publish", null, true).statusCode());
    assertEquals(409, request(admin, json, base, wordPath, "DELETE", null, true).statusCode());
    assertEquals(204, post(admin, json, base, wordPath + "/archive", null, true).statusCode());

    String listening = "admin/materials/LISTENING";
    var versions = request(admin, json, base, listening + "?size=50", "GET", null, false);
    assertEquals(200, versions.statusCode(), versions.body());
    var seed = json.readTree(versions.body()).get("items").values().stream()
        .filter(item -> item.get("id").asString().equals("201")).findFirst().orElseThrow();
    var fields = json.convertValue(seed, new tools.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
    fields.remove("id");
    fields.remove("status");
    fields.put("tts_voice_key", "invalid-voice");
    assertEquals(422, post(admin, json, base, listening, fields, true).statusCode());
    fields.put("tts_voice_key", seed.get("tts_voice_key").asString());
    var listeningDraft = post(admin, json, base, listening, fields, true);
    assertEquals(201, listeningDraft.statusCode(), listeningDraft.body());
    assertEquals(204, request(admin, json, base, listening + "/" + json.readTree(listeningDraft.body()).get("id").asString(),
        "DELETE", null, true).statusCode());

    var chapter = post(admin, json, base, topics, draft, true);
    assertEquals(201, chapter.statusCode(), chapter.body());
    String topicId = json.readTree(chapter.body()).get("id").asString();
    String lessons = "admin/materials/LESSONS";
    var lesson = new java.util.HashMap<>(java.util.Map.of("topic_id", topicId, "level", "C2",
        "title", "Lời chào", "summary", "Gặp bạn mới", "objective_code", "GREET", "learning_objective", "Chào và hỏi tên"));
    assertEquals(422, post(admin, json, base, lessons, lesson, true).statusCode());
    lesson.put("level", "A2");
    var lessonDraft = post(admin, json, base, lessons, lesson, true);
    assertEquals(201, lessonDraft.statusCode(), lessonDraft.body());
    String lessonId = json.readTree(lessonDraft.body()).get("id").asString();
    assertEquals(409, request(admin, json, base, topics + "/" + topicId, "DELETE", null, true).statusCode());
    assertEquals(422, post(admin, json, base, lessons + "/" + lessonId + "/publish", null, true).statusCode());
    assertEquals(204, post(admin, json, base, topics + "/" + topicId + "/publish", null, true).statusCode());
    var edited = new java.util.HashMap<>(draft);
    edited.put("name", "Chủ đề đã đổi tên");
    var updated = request(admin, json, base, topics + "/" + topicId, "PUT", edited, true);
    assertEquals(200, updated.statusCode(), updated.body());
    assertEquals("ACTIVE", json.readTree(updated.body()).get("status").asString());
    edited.put("code", "CHANGED_CODE");
    assertEquals(409, request(admin, json, base, topics + "/" + topicId, "PUT", edited, true).statusCode());
    assertEquals(204, post(admin, json, base, lessons + "/" + lessonId + "/publish", null, true).statusCode());

    var lines = java.util.stream.IntStream.rangeClosed(1, 4).mapToObj(i -> java.util.Map.of(
        "line_no", i, "speaker_label", i % 2 == 0 ? "Sam" : "Kim",
        "text_en", "Hello!", "text_vi", "Xin chào!", "voice_key", "en-us-voice-a-v1")).toList();
    var dialogue = post(admin, json, base, "admin/materials/DIALOGUES",
        java.util.Map.of("lesson_id", lessonId, "sort_order", 1, "title", "Gặp bạn", "lines_json", lines), true);
    assertEquals(201, dialogue.statusCode(), dialogue.body());
    assertEquals(204, post(admin, json, base, "admin/materials/DIALOGUES/"
        + json.readTree(dialogue.body()).get("id").asString() + "/publish", null, true).statusCode());

    var drill = post(admin, json, base, "admin/materials/PRONUNCIATION",
        java.util.Map.of("lesson_id", lessonId, "sort_order", 1, "reference_text", "How are you?",
            "locale", "en-US", "mode", "READ_ALOUD"), true);
    assertEquals(201, drill.statusCode(), drill.body());
    assertEquals(204, post(admin, json, base, "admin/materials/PRONUNCIATION/"
        + json.readTree(drill.body()).get("id").asString() + "/publish", null, true).statusCode());

    long exercise = 201;
    jdbc.update("UPDATE listening_exercises SET status='ACTIVE' WHERE id=?", exercise);
    try {
      var opened = post(learner, json, base, "listening/exercises/201/attempts",
          java.util.Map.of("clientRequestId", "material-history-" + suffix), true);
      assertEquals(200, opened.statusCode(), opened.body());
      String attempt = "listening/attempts/" + json.readTree(opened.body()).get("id").asString();
      assertEquals(200, post(learner, json, base, attempt + "/submit",
          java.util.Map.of("draftVersion", 0, "answers", java.util.Map.of()), true).statusCode());
      assertEquals(204, post(admin, json, base, listening + "/201/archive", null, true).statusCode());
      var history = request(learner, json, base, attempt, "GET", null, false);
      assertEquals(200, history.statusCode(), history.body());
      assertEquals(0, json.readTree(history.body()).get("result").get("score").asInt());
      assertFalse(json.readTree(history.body()).get("result").get("transcriptEn").asString().isBlank());
      assertEquals(409, request(admin, json, base, listening + "/201", "DELETE", null, true).statusCode());
    } finally {
      jdbc.update("DELETE FROM listening_attempts WHERE user_id=? AND exercise_id=?", Long.parseLong(student.id()), exercise);
      jdbc.update("UPDATE listening_exercises SET status='DRAFT' WHERE id=?", exercise);
    }
  }
  private static ScenarioInput adminScenario(
      String code, String title, String status, List<String> levels) {
    return adminScenario(code, title, status, levels, null);
  }

  private static ScenarioInput adminScenario(
      String code, String title, String status, List<String> levels, List<ScenarioVariant> variants) {
    return new ScenarioInput(code, title,
        "A synthetic lesson.", "Customer", "Waiter", "Order soup.", "At a fictional cafe.",
        "Ask the A2 learner to order soup.", status, levels, variants);
  }

  @Test
  void selectingRoleChangesPromptAndPreservesSnapshotAndRequestIdentity() {
    var owner = owner();
    String scenario = jdbc.queryForObject(
        "SELECT CAST(id AS CHAR) FROM scenarios WHERE code='SHOP_EXCHANGE'", String.class);
    String key = "exchange-reverse";
    var reverse = new CreatePracticeRequest(scenario, "B1", key, "VOICE", "REVERSE");
    var created = service.create(owner, reverse);
    assertEquals("REVERSE", created.roleCode());
    assertEquals("Hi, I'd like to exchange this shirt.", created.messages().getFirst().content());
    assertEquals("Nhân viên quầy đổi hàng", created.learnerRole());
    assertEquals("Khách hàng", created.aiRole());
    assertTrue(created.communicationGoal().contains("hai phương án"));
    assertTrue(provider.lastPrompt.instruction().contains("Play a customer"));
    assertTrue(provider.lastPrompt.instruction().contains("Open naturally in the partner role"));
    assertFalse(provider.lastPrompt.instruction().contains("one relevant question"));
    assertEquals(created.id(), service.create(owner, reverse).id());
    assertEquals(409, assertThrows(ApiException.class,
        () -> service.create(owner, new CreatePracticeRequest(scenario, "B1", key, "VOICE"))).status);
    assertEquals(404, assertThrows(ApiException.class, () -> service.create(owner,
        new CreatePracticeRequest(scenario, "B1", "unknown-role", "TEXT", "MISSING"))).status);

    assertEquals(404, assertThrows(ApiException.class, () -> service.create(owner,
        new CreatePracticeRequest(scenario, "A2", "exchange-wrong-level", "TEXT"))).status);

    var standard = service.create(owner, new CreatePracticeRequest(scenario, "B1", "exchange-default"));
    assertEquals("DEFAULT", standard.roleCode());
    assertEquals("Khách hàng", standard.learnerRole());
    assertEquals("Nhân viên quầy đổi hàng", standard.aiRole());
    assertEquals("TEXT", standard.mode());
    assertTrue(provider.lastPrompt.instruction().contains("Play the returns clerk"));
    long id = Long.parseLong(created.id());
    String roleSql = "UPDATE scenario_role_variants SET learner_role=? WHERE scenario_id=? AND code='REVERSE'";
    jdbc.update(roleSql, "Changed fixture role", Long.parseLong(scenario));
    try {
      var reopened = service.get(owner, id);
      assertEquals("B1", reopened.selectedLevel());
      assertEquals("VOICE", reopened.mode());
      assertEquals("REVERSE", reopened.roleCode());
      assertEquals(created.messages(), reopened.messages());
      assertTrue(learning.history(owner, 0, 10).items().stream().anyMatch(entry -> entry.id().equals(created.id()) && entry.title().equals(created.title())));
      assertEquals(created.learnerRole(), reopened.learnerRole());
      assertEquals(created.communicationGoal(), reopened.communicationGoal());
      service.send(owner, id, new SendTextRequest("May I see your receipt?", "exchange-role-turn"));
      assertTrue(provider.lastPrompt.instruction().contains("Learner role: Nhân viên quầy đổi hàng"));
      assertFalse(provider.lastPrompt.instruction().contains("Changed fixture role"));
      assertEquals("COMPLETED", service.finish(owner, id).status());
      provider.feedbackOutput = CLEAN_REVIEW;
      try {
        assertEquals("SUCCEEDED", service.generateFeedback(owner, id, false, false).status());
      } finally {
        provider.feedbackOutput = null;
      }
    } finally {
      jdbc.update(roleSql, created.learnerRole(), Long.parseLong(scenario));
    }
  }

  @Test
  void explainsLatestAssistantOnceAndKeepsItOutsideDialogue() throws Exception {
    var owner = owner();
    var practice = service.create(owner, voicePractice());
    long id = Long.parseLong(practice.id());
    long opening = Long.parseLong(practice.messages().getFirst().id());
    var input = new ExplanationRequest("explain-first", false, false);
    assertEquals("NOT_STARTED", service.getExplanation(owner, id, opening).status());
    assertEquals(404, assertThrows(ApiException.class,
        () -> service.explain(owner(), id, opening, input)).status);
    int before = provider.calls.get();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    provider.entered = entered;
    provider.release = release;
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var job = executor.submit(() -> service.explain(owner, id, opening, input));
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertEquals("RUNNING", service.explain(owner, id, opening, input).status());
        assertEquals(409, assertThrows(ApiException.class, () -> service.send(owner, id,
            new SendTextRequest("Soup, please.", "explain-overlap"))).status);
      } finally {
        release.countDown();
        provider.entered = null;
        provider.release = null;
      }
      var result = job.get(5, TimeUnit.SECONDS);
      assertEquals("SUCCEEDED", result.status());
      assertEquals("What do you want to order?", result.simpleEnglish());
      assertEquals("Bạn muốn gọi món gì?", result.meaningVi());
      assertEquals(result, service.getExplanation(owner, id, opening));
      assertEquals(result, service.explain(owner, id, opening, input));
      assertEquals(before + 1, provider.calls.get());
    }
    assertEquals(practice.messages(), service.get(owner, id).messages());
    assertEquals(0, service.get(owner, id).learnerTurns());
    var sent = service.send(owner, id, new SendTextRequest("Soup, please.", "explain-normal-turn"));
    assertFalse(provider.lastPrompt.history().stream().anyMatch(item ->
        item.content().equals("What do you want to order?")));
    long newest = Long.parseLong(sent.messages().getLast().id());
    assertEquals(409, assertThrows(ApiException.class, () -> service.explain(owner, id, newest, input)).status);
    var another = service.create(owner, voicePractice());
    long anotherId = Long.parseLong(another.id());
    long anotherOpening = Long.parseLong(another.messages().getFirst().id());
    assertEquals(404, assertThrows(ApiException.class,
        () -> service.explain(owner, id, anotherOpening, input)).status);
    service.send(owner, anotherId, new SendTextRequest("Soup, please.", "another-chat-turn"));
    assertEquals(409, assertThrows(ApiException.class, () -> service.explain(owner, anotherId, anotherOpening,
        new ExplanationRequest("obsolete-explain", false, false))).status);
    assertEquals(404, assertThrows(ApiException.class, () -> service.getExplanation(owner, id,
        Long.parseLong(sent.messages().get(1).id()))).status);
    learning.deleteSession(owner, id);
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM assistant_explanations WHERE session_id=?",
        Integer.class, id));
  }

  @Test
  void explanationHandlesTimeoutValidationRetryAndQuota() {
    var owner = owner();
    var practice = service.create(owner, draft());
    long id = Long.parseLong(practice.id());
    long opening = Long.parseLong(practice.messages().getFirst().id());
    provider.timeout = true;
    var failure = service.explain(owner, id, opening, new ExplanationRequest("explain-retry", false, false));
    assertEquals("UNKNOWN", failure.status());
    assertTrue(failure.retryable());
    assertEquals(409, assertThrows(ApiException.class, () -> service.explain(owner, id, opening,
        new ExplanationRequest("explain-retry", true, false))).status);
    provider.explanationOutput = """
        {"simpleEnglish":"What do you want to order?","meaningVi":" "} {}
        """;
    try {
      var invalid = service.explain(owner, id, opening, new ExplanationRequest("explain-retry", true, true));
      assertEquals("EXPLANATION_INVALID", invalid.errorCode());
      assertFalse(invalid.retryable());
      assertNull(invalid.simpleEnglish());
    } finally {
      provider.explanationOutput = VALID_EXPLANATION;
    }
    var next = service.send(owner, id, new SendTextRequest("Soup please.", "explain-after-timeout"));
    assertEquals(409, assertThrows(ApiException.class, () -> service.explain(owner, id, opening,
        new ExplanationRequest("explain-retry", true, true))).status);
    Long operation = jdbc.queryForObject("SELECT ai_request_id FROM assistant_explanations WHERE session_id=?",
        Long.class, id);
    for (int number = 3; number <= 62; number++) {
      jdbc.update("""
          INSERT INTO ai_attempts(ai_request_id,attempt_no,model_name,status,started_at,finished_at)
          VALUES (?,?,'quota-fixture','FAILED',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
          """, operation, number);
    }
    long latest = Long.parseLong(next.messages().getLast().id());
    int before = provider.calls.get();
    assertEquals(429, assertThrows(ApiException.class, () -> service.explain(owner, id, latest,
        new ExplanationRequest("explain-quota", false, false))).status);
    assertEquals(before, provider.calls.get());
    assertEquals("NOT_STARTED", service.getExplanation(owner, id, latest).status());
  }

  @Test
  void aggregatesOnlyRepeatedErrorsAcrossDifferentMessages() {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    service.send(owner, id, new SendTextRequest("I want apple, I want orange.", "repeat-error-first"));
    service.send(owner, id, new SendTextRequest("I want egg.", "repeat-error-second"));
    var latest = service.send(owner, id, new SendTextRequest("I want a drink.", "repeat-error-third"));
    String first = latest.messages().get(1).id(), second = latest.messages().get(3).id();
    String third = latest.messages().get(5).id();
    String correction = """
        {"sourceMessageId":"%s","itemType":"%s","category":"GRAMMAR","ruleCode":"ARTICLE",
         "originalText":"%s","suggestedText":"%s","explanationVi":"Thêm mạo từ."}
        """;
    provider.feedbackOutput = CLEAN_REVIEW.replace("\"corrections\":[]", "\"corrections\":[" +
        correction.formatted(first, "ERROR", "want apple", "want an apple") + "," +
        correction.formatted(first, "ERROR", "want orange", "want an orange") + "," +
        correction.formatted(second, "ERROR", "want egg", "want an egg") + "," +
        correction.formatted(third, "IMPROVEMENT", "I want a drink", "May I have a drink") + "]");
    service.finish(owner, id);
    var result = service.generateFeedback(owner, id, false, false);
    assertEquals("SUCCEEDED", result.status());
    assertEquals(1, result.repeatedErrors().size());
    var group = result.repeatedErrors().getFirst();
    assertEquals("ARTICLE", group.ruleCode());
    assertEquals(2, group.messageCount());
    assertEquals(first, group.example().sourceMessageId());
    int count = provider.calls.get();
    assertEquals(result, service.getFeedback(owner, id));
    assertEquals(count, provider.calls.get());
  }

  @Test
  void finishCountsOnlyPracticesWithSuccessfulExchanges() {
    var owner = owner();
    long openingOnly = Long.parseLong(service.create(owner, draft()).id());
    long failedOnly = Long.parseLong(service.create(owner, draft()).id());
    long successful = Long.parseLong(service.create(owner, draft()).id());
    long failedLast = Long.parseLong(service.create(owner, draft()).id());

    assertEquals("ABANDONED", service.finish(owner, openingOnly).status());
    provider.timeout = true;
    service.send(owner, failedOnly, new SendTextRequest("Soup please.", "finish-first-failed"));
    assertEquals("ABANDONED", service.finish(owner, failedOnly).status());
    assertEquals("Soup please.", service.get(owner, failedOnly).messages().getLast().content());
    assertEquals(409, assertThrows(ApiException.class,
        () -> service.generateFeedback(owner, failedOnly, false, false)).status);
    assertEquals(0, learning.statistics(owner).completedSessions());
    assertEquals(1, learning.statistics(owner).learnerTurns());

    service.send(owner, successful, new SendTextRequest("Tea please.", "finish-successful"));
    assertEquals("COMPLETED", service.finish(owner, successful).status());
    service.send(owner, failedLast, new SendTextRequest("Water please.", "finish-first-success"));
    provider.timeout = true;
    service.send(owner, failedLast, new SendTextRequest("No ice please.", "finish-last-failed"));
    var result = service.finish(owner, failedLast);
    assertEquals("COMPLETED", result.status());
    assertEquals("No ice please.", result.messages().getLast().content());
    assertEquals(result, service.finish(owner, failedLast));
    assertEquals(2, learning.statistics(owner).completedSessions());
    var statuses = learning.history(owner, 0, 12).items().stream()
        .collect(java.util.stream.Collectors.toMap(entry -> entry.id(), entry -> entry.status()));
    assertEquals("ABANDONED", statuses.get(Long.toString(failedOnly)));
    assertEquals("COMPLETED", statuses.get(Long.toString(failedLast)));
    provider.feedbackOutput = CLEAN_REVIEW;
    assertEquals("SUCCEEDED", service.generateFeedback(owner, failedLast, false, false).status());
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "G6_AZURE_FIXTURE", matches = "true")
  void queuesOriginalVoiceOnlyAfterConfirmation() throws Exception {
    org.mockito.Mockito.when(pronunciationProvider.available()).thenReturn(true);
    org.mockito.Mockito.when(pronunciationProvider.assess(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.eq("en-US")))
        .thenThrow(new vn.englishconversation.pronunciation.service.PronunciationProvider.Failure(
            "AZURE_OUTCOME_UNKNOWN", false, true));
    var owner = owner();
    long session = Long.parseLong(service.create(owner, voicePractice()).id());
    long audio = Long.parseLong(service.uploadVoice(owner, session, "azure-fixture-audio",
        WaveAudio.read(wave(16000, 123, 1)), false).id());
    String count = "SELECT COUNT(*) FROM pronunciation_attempts WHERE audio_submission_id=?";
    assertEquals(0, jdbc.queryForObject(count, Integer.class, audio));
    var confirmed = service.confirmVoice(owner, session, audio, "This is the edited text.", true);
    assertEquals("This is the edited text.", confirmed.messages().get(1).content());
    assertEquals(1, service.confirmVoice(owner, session, audio, "This is the edited text.", true).learnerTurns());
    assertEquals(1, jdbc.queryForObject(count, Integer.class, audio));
    assertEquals("COMPLETED", service.finish(owner, session).status());
    for (int n = 0; n < 45; n++) {
      if ("UNKNOWN".equals(service.get(owner, session).messages().get(1).pronunciation().status())) return;
      Thread.sleep(200);
    }
    fail("Expected Azure fixture to complete independently of CHAT");
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "G6_AZURE_FIXTURE", matches = "true")
  void keepsChatAvailableForExpiredAudioAndReleasesFailedRecordingForRetry() {
    org.mockito.Mockito.when(pronunciationProvider.available()).thenReturn(true);
    var owner = owner();
    long session = Long.parseLong(service.create(owner, voicePractice()).id());
    var audio = WaveAudio.read(wave(16000, 123, 1));
    provider.timeout = true;
    var failed = service.uploadVoice(owner, session, "azure-enabled-retry", audio, false);
    assertEquals("FAILED", failed.status());
    long draftId = Long.parseLong(failed.id());
    assertNull(jdbc.queryForObject("SELECT storage_key FROM audio_submissions WHERE id=?", String.class, draftId));
    assertEquals("READY", service.uploadVoice(owner, session, "azure-enabled-retry", audio, true).status());
    assertNotNull(jdbc.queryForObject("SELECT storage_key FROM audio_submissions WHERE id=?", String.class, draftId));
    jdbc.update("UPDATE audio_submissions SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?", draftId);
    voiceStorage.sweep();
    assertNull(jdbc.queryForObject("SELECT storage_key FROM audio_submissions WHERE id=?", String.class, draftId));
    var confirmed = service.confirmVoice(owner, session, draftId, "Edited text.", true);
    assertEquals(1, confirmed.learnerTurns());
    assertNull(confirmed.messages().get(1).pronunciation());
    org.mockito.Mockito.verify(pronunciationProvider, org.mockito.Mockito.never())
        .assess(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void rejectsNoSpeechWithoutCreatingALearnerTurn() {
    var owner = owner();
    long session = Long.parseLong(service.create(owner, voicePractice()).id());
    provider.transcriptionOutput = "NO_SPEECH";
    try {
      var result = service.uploadVoice(owner, session, "no-speech-fixture",
          WaveAudio.read(wave(16000, 123, 1)), false);
      assertEquals("FAILED", result.status());
      assertEquals("NO_SPEECH", result.errorCode());
      assertNull(result.transcript());
      assertEquals(0, service.get(owner, session).learnerTurns());
    } finally {
      provider.transcriptionOutput = "A bowl of soup.";
    }
  }

  @Test
  void voiceKeepsDraftSeparateAndClaimsOneEditedTurn() {
    var owner = owner();
    var other = owner();
    long id = Long.parseLong(service.create(owner, voicePractice()).id());
    int before = provider.calls.get();
    WaveAudio wave = WaveAudio.read(wave(16000, 123, 1));
    var uploaded = service.uploadVoice(owner, id, "voice-one-take", wave, false);
    long audioId = Long.parseLong(uploaded.id());
    assertEquals(1000, uploaded.durationMillis());
    assertEquals("READY", uploaded.status());
    assertEquals("A bowl of soup.", uploaded.transcript());
    assertEquals(0, service.get(owner, id).learnerTurns());
    assertEquals(1, service.get(owner, id).messages().size());
    assertEquals(uploaded, service.uploadVoice(owner, id, "voice-one-take", wave, false));
    var newer = service.uploadVoice(owner, id, "voice-newer-take", wave, false);
    assertEquals(newer, service.recoverVoice(owner, id).orElseThrow());
    service.discardVoice(owner, id, Long.parseLong(newer.id()));
    assertEquals(uploaded, service.recoverVoice(owner, id).orElseThrow());
    assertEquals(404, assertThrows(ApiException.class, () -> service.recoverVoice(other, id)).status);
    assertEquals(before + 2, provider.calls.get());
    assertNull(jdbc.queryForObject("SELECT storage_key FROM audio_submissions WHERE id=?", String.class, audioId));
    assertEquals(404, assertThrows(ApiException.class, () -> service.getVoice(other, id, audioId)).status);
    assertEquals(409, assertThrows(ApiException.class, () -> service.uploadVoice(
        owner, id, "voice-one-take", WaveAudio.read(wave(16000, 124, 1)), false)).status);
    var response = service.confirmVoice(owner, id, audioId, "I would like a bowl of soup.");
    assertEquals(1, response.learnerTurns());
    assertEquals("I would like a bowl of soup.", response.messages().get(1).content());
    assertEquals("LEARNER_AUDIO", response.turns().getLast().kind());
    assertEquals(1, service.confirmVoice(owner, id, audioId, "I would like a bowl of soup.").learnerTurns());
    assertEquals(before + 3, provider.calls.get());
    assertTrue(service.recoverVoice(owner, id).isEmpty());
    assertEquals(409, assertThrows(ApiException.class, () -> service.confirmVoice(owner, id, audioId, "An edit too late.")).status);
    assertNull(service.getVoice(owner, id, audioId).transcript());
    assertEquals("AUDIO", jdbc.queryForObject("SELECT input_type FROM messages WHERE audio_submission_id=?", String.class, audioId));
  }

  @Test
  void voiceTimeoutRetryAndIndependentDraftExpiry() {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, voicePractice()).id());
    WaveAudio wave = WaveAudio.read(wave(160, 47, 1));
    provider.timeout = true;
    var failure = service.uploadVoice(owner, id, "voice-retry-id", wave, false);
    assertEquals("FAILED", failure.status());
    assertTrue(failure.unknown());
    assertTrue(failure.retryable());
    assertEquals(failure, service.uploadVoice(owner, id, "voice-retry-id", wave, false));
    var ready = service.uploadVoice(owner, id, "voice-retry-id", wave, true);
    assertEquals(failure.id(), ready.id());
    assertEquals("READY", ready.status());
    long key = Long.parseLong(ready.id());
    jdbc.update("UPDATE audio_submissions SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?", key);
    voiceStorage.sweep();
    assertEquals("READY", service.getVoice(owner, id, key).status());
    assertEquals(ready, service.recoverVoice(owner, id).orElseThrow());
    jdbc.update("UPDATE audio_submissions SET transcript_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?", key);
    assertEquals("EXPIRED", service.getVoice(owner, id, key).status());
    assertTrue(service.recoverVoice(owner, id).isEmpty());
    assertEquals(409, assertThrows(ApiException.class, () -> service.confirmVoice(owner, id, key, "Too late.")).status);
    voiceStorage.sweep();
    assertNull(jdbc.queryForObject("SELECT draft_transcript FROM audio_submissions WHERE id=?", String.class, key));
    assertEquals(0, service.get(owner, id).learnerTurns());
  }

  @Test
  void deletingPracticeDuringTranscriptionKeepsCleanupReferenceAndBlocksLateDraft() throws Exception {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, voicePractice()).id());
    WaveAudio wave = WaveAudio.read(wave(160, 123, 1));
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    provider.entered = entered;
    provider.release = release;
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var call = pool.submit(() -> service.uploadVoice(owner, id, "voice-delete-running", wave, false));
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        var replay = service.uploadVoice(owner, id, "voice-delete-running", wave, false);
        assertEquals("TRANSCRIBING", replay.status());
        assertEquals(replay, service.recoverVoice(owner, id).orElseThrow());
        assertEquals(409, assertThrows(ApiException.class, () -> service.send(
            owner, id, new SendTextRequest("Soup please.", "voice-racing-text"))).status);
        learning.deleteSession(owner, id);
        long key = Long.parseLong(replay.id());
        assertEquals("DELETION_PENDING", jdbc.queryForObject("SELECT status FROM audio_submissions WHERE id=?", String.class, key));
        assertNotNull(jdbc.queryForObject("SELECT storage_key FROM audio_submissions WHERE id=?", String.class, key));
        voiceStorage.sweep();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audio_submissions WHERE id=?", Integer.class, key));
      } finally {
        release.countDown();
        provider.entered = null;
        provider.release = null;
      }
      var failed = assertThrows(ExecutionException.class, () -> call.get(5, TimeUnit.SECONDS));
      assertEquals(404, assertInstanceOf(ApiException.class, failed.getCause()).status);
    }
  }

  private CreatePracticeRequest voicePractice() {
    var request = draft();
    return new CreatePracticeRequest(request.scenarioId(), request.level(), request.clientRequestId(), "VOICE");
  }

  @Test
  void validatesActualPcmAndDuration() {
    assertEquals(400, assertThrows(ApiException.class, () -> WaveAudio.read(wave(1, 0, 1))).status);
    assertEquals(400, assertThrows(ApiException.class, () -> WaveAudio.read(wave(1, 10, 3))).status);
    assertEquals(413, assertThrows(ApiException.class, () -> WaveAudio.read(wave(960001, 10, 1))).status);
    assertEquals(60000, WaveAudio.read(wave(960000, 10, 1)).durationMillis());
  }

  private MockMultipartFile wave(int frames, int sample, int format) {
    ByteBuffer bytes = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(0x46464952).putInt(bytes.capacity() - 8).putInt(0x45564157)
        .putInt(0x20746d66).putInt(16).putShort((short) format).putShort((short) 1)
        .putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16)
        .putInt(0x61746164).putInt(frames * 2).putShort((short) sample);
    return new MockMultipartFile("file", "recording.wav", "audio/wav", bytes.array());
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "G6_AZURE_FIXTURE", matches = "true")
  void deletionKeepsAzureFileReferenceUntilWorkerCleansIt() throws Exception {
    org.mockito.Mockito.when(pronunciationProvider.available()).thenReturn(true);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var path = new java.util.concurrent.atomic.AtomicReference<java.nio.file.Path>();
    org.mockito.Mockito.when(pronunciationProvider.assess(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.eq("en-US"))).thenAnswer(call -> {
          path.set(call.getArgument(0));
          entered.countDown();
          if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture timed out");
          var score = java.math.BigDecimal.valueOf(85);
          return new vn.englishconversation.pronunciation.dto.PronunciationData.Assessment(
              "Late result", score, score, score, null, List.of());
        });
    var owner = owner();
    long session = Long.parseLong(service.create(owner, voicePractice()).id());
    long audio = Long.parseLong(service.uploadVoice(owner, session, "delete-azure-audio",
        WaveAudio.read(wave(16000, 123, 1)), false).id());
    service.confirmVoice(owner, session, audio, "My text.", true);
    try {
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      learning.deleteSession(owner, session);
      voiceStorage.sweep();
      assertEquals(1, jdbc.queryForObject(
          "SELECT COUNT(*) FROM audio_submissions WHERE id=? AND status='DELETION_PENDING'",
          Integer.class, audio));
    } finally {
      release.countDown();
    }
    for (int n = 0; n < 45 && java.nio.file.Files.exists(path.get()); n++) Thread.sleep(150);
    assertFalse(java.nio.file.Files.exists(path.get()));
    voiceStorage.sweep();
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pronunciation_attempts WHERE audio_submission_id=?",
        Integer.class, audio));
    assertEquals(404, assertThrows(ApiException.class, () -> service.get(owner, session)).status);
  }

  @Test
  void learningCardsSurviveSourceDeletionAndWholeDeleteIsOwnerScoped() {
    var owner = owner();
    var other = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    long otherId = Long.parseLong(service.create(other, draft()).id());
    service.send(owner, id, new SendTextRequest("Soup, please.", "learn-card-turn"));
    service.finish(owner, id);
    provider.feedbackOutput = CLEAN_REVIEW;
    long suggestion =
        Long.parseLong(
            service.generateFeedback(owner, id, false, false).suggestionIds().getFirst());
    assertEquals(
        404, assertThrows(ApiException.class, () -> learning.save(other, suggestion)).status);
    var saved = learning.save(owner, suggestion);
    assertEquals(saved, learning.save(owner, suggestion));
    long card = Long.parseLong(saved.id());
    var answer =
        new vn.englishconversation.learning.dto.LearningData.ReviewAnswer(
            "REMEMBERED", "review-attempt-1");
    assertEquals(
        404, assertThrows(ApiException.class, () -> learning.answer(other, card, answer)).status);
    assertEquals("REMEMBERED", learning.answer(owner, card, answer).status());
    assertEquals("REMEMBERED", learning.answer(owner, card, answer).status());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM review_attempts WHERE review_item_id=?", Integer.class, card));
    assertEquals(
        409,
        assertThrows(
                ApiException.class,
                () ->
                    learning.answer(
                        owner,
                        card,
                        new vn.englishconversation.learning.dto.LearningData.ReviewAnswer(
                            "NEEDS_REVIEW", "review-attempt-1")))
            .status);
    assertEquals(1, learning.history(owner, 0, 1).total());
    assertEquals(Long.toString(id), learning.history(owner, 0, 1).items().getFirst().id());
    assertTrue(learning.history(owner, 1, 1).items().isEmpty());
    assertEquals(Long.toString(otherId), learning.history(other, 0, 1).items().getFirst().id());
    assertEquals(
        404, assertThrows(ApiException.class, () -> learning.deleteSession(other, id)).status);
    learning.deleteSession(owner, id);
    assertEquals(404, assertThrows(ApiException.class, () -> service.get(owner, id)).status);
    assertEquals(
        404, assertThrows(ApiException.class, () -> service.getFeedback(owner, id)).status);
    assertEquals(
        404, assertThrows(ApiException.class, () -> learning.save(owner, suggestion)).status);
    var retained = learning.cards(owner, null, 0, 12).items().getFirst();
    assertEquals(saved.expression(), retained.expression());
    assertEquals("REMEMBERED", retained.status());
    assertNull(retained.sourceSuggestionId());
    assertEquals(0, learning.history(owner, 0, 12).total());
    assertEquals(0, learning.statistics(owner).learnerTurns());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ai_requests WHERE user_id=? AND session_id IS NOT NULL",
            Integer.class,
            owner.id()));
    assertTrue(
        jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_requests WHERE user_id=?", Integer.class, owner.id())
            > 0);
    learning.deleteAll(owner);
    learning.deleteAll(owner);
    assertEquals(0, learning.cards(owner, null, 0, 12).total());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM review_attempts WHERE review_item_id=?", Integer.class, card));
    assertEquals(Long.toString(otherId), service.get(other, otherId).id());
  }

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
    service.hint(owner, id, new HintRequest("stats-hint-001"));
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
            + " ?,topic_id,UTC_TIMESTAMP(6) FROM scenarios WHERE id=?",
        owner.id(),
        Long.parseLong(draft().scenarioId()));
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

  @Test
  void accountDeletionRejectsLateAiAndRemovesPracticeAudioAndRequests() throws Exception {
    var owner=owner();long id=Long.parseLong(service.create(owner,draft()).id());
    service.uploadVoice(owner,id,"account-delete-audio",WaveAudio.read(wave(160,47,1)),false);
    provider.entered=new CountDownLatch(1);provider.release=new CountDownLatch(1);
    try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
      var pending=executor.submit(()->service.send(owner,id,new SendTextRequest("Soup, please.","account-delete-running")));
      try {
        assertTrue(provider.entered.await(5,TimeUnit.SECONDS));
        lifecycle.delete(owner,"Fixture account secret only","DELETE");deletion.sweep();
      } finally { provider.release.countDown(); }
      var failure=assertThrows(ExecutionException.class,()->pending.get(6,TimeUnit.SECONDS));
      assertEquals(401,assertInstanceOf(ApiException.class,failure.getCause()).status);
      for(String table:List.of("users","practice_sessions","ai_requests","review_items"))
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+(table.equals("users")?"id":"user_id")+"=?",Integer.class,owner.id()));
      assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM audio_submissions WHERE session_id=?",Integer.class,id));
    } finally { provider.entered=null;provider.release=null; }
  }

  @Test
  void deletingWhileProviderRunsDiscardsLateResultAndDetachesMetadata() throws Exception {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    provider.entered = new CountDownLatch(1);
    provider.release = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var pending =
          executor.submit(
              () ->
                  service.send(
                      owner, id, new SendTextRequest("Soup, please.", "late-after-delete")));
      try {
        assertTrue(provider.entered.await(5, TimeUnit.SECONDS));
        learning.deleteSession(owner, id);
      } finally {
        provider.release.countDown();
      }
      var failure = assertThrows(ExecutionException.class, () -> pending.get(6, TimeUnit.SECONDS));
      assertEquals(404, assertInstanceOf(ApiException.class, failure.getCause()).status);
    } finally {
      provider.entered = null;
      provider.release = null;
    }
    assertEquals(
        0,
        jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE session_id=?", Integer.class, id));
    assertEquals(
        1,
        jdbc.queryForObject(
            """
SELECT COUNT(*) FROM ai_attempts a JOIN ai_requests r ON r.id=a.ai_request_id
WHERE r.user_id=? AND r.session_id IS NULL AND a.status='UNKNOWN' AND a.error_code='PRACTICE_DELETED'
""",
            Integer.class,
            owner.id()));
  }

  @Test
  void httpFlowEnforcesSessionAndCsrf() throws Exception {
    var browser =
        java.net.http.HttpClient.newBuilder()
            .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL))
            .build();
    var json = new tools.jackson.databind.ObjectMapper();
    String base = "http://127.0.0.1:" + port + "/api/v1/";
    var anonymous =
        browser.send(
            java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "practice-sessions/1"))
                .GET()
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(401, anonymous.statusCode());
    var credentials =
        java.util.Map.of(
            "email",
            "http-chat-" + java.util.UUID.randomUUID() + "@example.test",
            "password",
            "Synthetic HTTP fixture password");
    assertEquals(201, post(browser, json, base, "auth/register", credentials, true).statusCode());
    assertEquals(200, post(browser, json, base, "auth/login", credentials, true).statusCode());
    assertEquals(403, post(browser, json, base, "practice-sessions", draft(), false).statusCode());
    var input = draft();
    var voiceInput = new CreatePracticeRequest(input.scenarioId(), input.level(), input.clientRequestId(), "VOICE");
    var invalidInput = new CreatePracticeRequest(input.scenarioId(), input.level(), input.clientRequestId(), "invalid");
    assertEquals(400, post(browser, json, base, "practice-sessions", invalidInput, true).statusCode());
    var created = post(browser, json, base, "practice-sessions", voiceInput, true);
    assertEquals(200, created.statusCode(), created.body());
    var opening = json.readTree(created.body());
    assertEquals("VOICE", opening.get("mode").asString());
    assertFalse(opening.has("instruction"));
    String path = "practice-sessions/" + opening.get("id").asString();
    String explanationPath = path + "/messages/" + opening.get("messages").get(0).get("id").asString()
        + "/explanation";
    var explanationInput = new ExplanationRequest("http-explanation", false, false);
    assertEquals(403, post(browser, json, base, explanationPath, explanationInput, false).statusCode());
    var explanation = post(browser, json, base, explanationPath, explanationInput, true);
    assertEquals(200, explanation.statusCode(), explanation.body());
    assertEquals("SUCCEEDED", json.readTree(explanation.body()).get("status").asString());
    assertEquals(json.readTree(explanation.body()), json.readTree(
        request(browser, json, base, explanationPath, "GET", null, false).body()));
    assertEquals(403, multipart(browser, json, base, path, false).statusCode());
    var voice = multipart(browser, json, base, path, true);
    assertEquals(200, voice.statusCode(), voice.body());
    var voiceBody = json.readTree(voice.body());
    assertEquals("READY", voiceBody.get("status").asString());
    assertFalse(voiceBody.has("storageKey"));
    var recovered = request(browser, json, base, path + "/audio", "GET", null, false);
    assertEquals(200, recovered.statusCode());
    assertEquals(voiceBody, json.readTree(recovered.body()));
    assertEquals(401, request(java.net.http.HttpClient.newHttpClient(), json, base,
        path + "/audio", "GET", null, false).statusCode());
    String voicePath = path + "/audio/" + voiceBody.get("id").asString();
    assertEquals(403, post(browser, json, base, voicePath + "/confirm",
        java.util.Map.of("content", "I would like soup."), false).statusCode());
    var sent =
        post(
            browser,
            json,
            base,
            path + "/turns",
            new SendTextRequest("Soup, please.", "http-turn-1"),
            true);
    assertEquals(200, sent.statusCode());
    assertEquals(3, json.readTree(sent.body()).get("messages").size());
    var hint = post(browser, json, base, path + "/hints", new HintRequest("http-hint-1"), true);
    assertEquals(200, hint.statusCode());
    assertTrue(json.readTree(hint.body()).get("hint").isString());
    assertEquals(3, json.readTree(hint.body()).get("messages").size());
    long sessionId = Long.parseLong(opening.get("id").asString());
    var switchRequest = java.util.Map.of("mode", "TEXT");
    assertEquals("VOICE", json.readTree(
        request(browser, json, base, path, "GET", null, false).body()).get("mode").asString());
    assertEquals(403, request(browser, json, base, path + "/mode", "PATCH", switchRequest, false).statusCode());
    assertEquals(404, assertThrows(ApiException.class, () -> service.switchToText(owner(), sessionId)).status);
    assertEquals(400, request(browser, json, base, path + "/mode", "PATCH",
        java.util.Map.of("mode", "VOICE"), true).statusCode());
    var switched = request(browser, json, base, path + "/mode", "PATCH", switchRequest, true);
    assertEquals(200, switched.statusCode(), switched.body());
    var afterSwitch = json.readTree(switched.body());
    assertEquals("TEXT", afterSwitch.get("mode").asString());
    assertEquals(1, afterSwitch.get("learnerTurns").asInt());
    assertEquals(json.readTree(sent.body()).get("messages"), afterSwitch.get("messages"));
    assertEquals(afterSwitch, json.readTree(request(browser, json, base,
        path + "/mode", "PATCH", switchRequest, true).body()));
    int beforeReplay = provider.calls.get();
    var replay = post(browser, json, base, "practice-sessions", voiceInput, true);
    assertEquals(200, replay.statusCode(), replay.body());
    assertEquals(afterSwitch.get("id"), json.readTree(replay.body()).get("id"));
    assertEquals("TEXT", json.readTree(replay.body()).get("mode").asString());
    assertEquals(409, post(browser, json, base, "practice-sessions", input, true).statusCode());
    assertEquals(beforeReplay, provider.calls.get());
    var finished = post(browser, json, base, path + "/finish", java.util.Map.of(), true);
    assertEquals("COMPLETED", json.readTree(finished.body()).get("status").asString());
    assertEquals(204, request(browser, json, base, path + "/audio", "GET", null, false).statusCode());
    assertEquals(
        403, post(browser, json, base, path + "/feedback", java.util.Map.of(), false).statusCode());
    provider.feedbackOutput = CLEAN_REVIEW;
    var review = post(browser, json, base, path + "/feedback", java.util.Map.of(), true);
    assertEquals(200, review.statusCode(), review.body());
    assertEquals("SUCCEEDED", json.readTree(review.body()).get("status").asString());
    var reloaded =
        browser.send(
            java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + path + "/feedback"))
                .GET()
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(200, reloaded.statusCode());
    assertEquals(json.readTree(review.body()), json.readTree(reloaded.body()));
    String suggestion = json.readTree(review.body()).get("suggestionIds").get(0).asString();
    var card =
        post(
            browser,
            json,
            base,
            "learning/cards/from-suggestion/" + suggestion,
            java.util.Map.of(),
            true);
    assertEquals(200, card.statusCode(), card.body());
    assertEquals(suggestion, json.readTree(card.body()).get("sourceSuggestionId").asString());
    String history = base + "learning/history/" + opening.get("id").asString();
    var delete = java.net.http.HttpRequest.newBuilder(java.net.URI.create(history)).DELETE();
    assertEquals(
        403,
        browser
            .send(delete.build(), java.net.http.HttpResponse.BodyHandlers.discarding())
            .statusCode());
    var csrf =
        json.readTree(
            browser
                .send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "auth/csrf"))
                        .GET()
                        .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString())
                .body());
    delete.header(csrf.get("headerName").asString(), csrf.get("token").asString());
    assertEquals(
        204,
        browser
            .send(delete.build(), java.net.http.HttpResponse.BodyHandlers.discarding())
            .statusCode());
    var stats =
        browser.send(
            java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "learning/stats"))
                .GET()
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(200, stats.statusCode());
    assertEquals(0, json.readTree(stats.body()).get("learnerTurns").asInt());
  }

  private java.net.http.HttpResponse<String> post(
      java.net.http.HttpClient client,
      tools.jackson.databind.ObjectMapper json,
      String base,
      String path,
      Object body,
      boolean csrf)
      throws Exception {
    return request(client, json, base, path, "POST", body, csrf);
  }

  private java.net.http.HttpResponse<String> request(
      java.net.http.HttpClient client, tools.jackson.databind.ObjectMapper json, String base,
      String path, String method, Object body, boolean csrf) throws Exception {
    var request =
        java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + path))
            .header("Content-Type", "application/json");
    if (csrf) {
      var response =
          client.send(
              java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "auth/csrf"))
                  .GET()
                  .build(),
              java.net.http.HttpResponse.BodyHandlers.ofString());
      var token = json.readTree(response.body());
      request.header(token.get("headerName").asString(), token.get("token").asString());
    }
    return client.send(
        request
            .method(method, body == null ? java.net.http.HttpRequest.BodyPublishers.noBody()
                : java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build(),
        java.net.http.HttpResponse.BodyHandlers.ofString());
  }

  private java.net.http.HttpResponse<String> multipart(
      java.net.http.HttpClient browser, tools.jackson.databind.ObjectMapper json, String base,
      String path, boolean withCsrf) throws Exception {
    String boundary = "fixture-audio";
    var buffer = new java.io.ByteArrayOutputStream();
    buffer.writeBytes(("--" + boundary
        + "\r\nContent-Disposition: form-data; name=\"clientRequestId\"\r\n\r\nhttp-voice-fixture\r\n--"
        + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"capture.wav\""
        + "\r\nContent-Type: audio/wav\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    buffer.writeBytes(wave(160, 900, 1).getBytes());
    buffer.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + path + "/audio"))
        .header("Content-Type", "multipart/form-data; boundary=" + boundary);
    if (withCsrf) {
      var csrf = browser.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "auth/csrf"))
          .GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
      var token = json.readTree(csrf.body());
      req.header(token.get("headerName").asString(), token.get("token").asString());
    }
    return browser.send(req.POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(buffer.toByteArray())).build(),
        java.net.http.HttpResponse.BodyHandlers.ofString());
  }

  @DynamicPropertySource
  static void isolated(DynamicPropertyRegistry properties) {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:(?:3307/phase[12]_auth_[0-9]{14}|13306/g6_test_[0-9a-f]{12})\\?connectionTimeZone=UTC"))
      throw new IllegalStateException("Use the isolated MySQL test script");
    properties.add("spring.datasource.url", () -> url);
  }

  @TestConfiguration
  static class FixtureConfig {
    @Bean
    @Primary
    FixtureProvider fixtureProvider() {
      return new FixtureProvider();
    }
  }

  static class FixtureProvider implements TextProvider {
    final AtomicInteger calls = new AtomicInteger();
    volatile boolean timeout;
    volatile String transcriptionOutput = "A bowl of soup.";
    volatile String feedbackOutput;
    volatile String explanationOutput = VALID_EXPLANATION;
    volatile Prompt lastPrompt;
    volatile CountDownLatch entered;
    volatile CountDownLatch release;

    public boolean ready() {
      return true;
    }

    public String model() {
      return "fixture-only";
    }

    public Outcome generate(Prompt prompt) {
      if (TransactionSynchronizationManager.isActualTransactionActive())
        throw new AssertionError("Provider called inside a transaction");
      calls.incrementAndGet();
      lastPrompt = prompt;
      if (entered != null) {
        entered.countDown();
        try {
          if (!release.await(8, TimeUnit.SECONDS))
            throw new AssertionError("Test did not release provider");
        } catch (InterruptedException failure) {
          throw new AssertionError(failure);
        }
      }
      if (timeout) {
        timeout = false;
        return Outcome.failure("PROVIDER_TIMEOUT", true, false, 0);
      }
      if (prompt.waveAudio() != null)
        return new Outcome(transcriptionOutput, "fixture-only", "wave-fixture",
            40L, 6L, false, null, false, false, 0);
      if (prompt.schema().toString().contains("simpleEnglish"))
        return new Outcome(explanationOutput, model(), "explanation-fixture", 21L, 8L,
            false, null, false, false, 0);
      if (!prompt.schema().isEmpty()) {
        assertTrue(prompt.instruction().contains("Never grade pronunciation"));
        return new Outcome(
            feedbackOutput,
            "fixture-only",
            "feedback-fixture",
            31L,
            18L,
            false,
            null,
            false,
            false,
            0);
      }
      assertTrue(prompt.instruction().contains("A2") || prompt.instruction().contains("B1"));
      String reply = prompt.instruction().contains("Open naturally in the partner role")
          && prompt.instruction().contains("Play a customer")
          ? "Hi, I'd like to exchange this shirt." : "Hello! What would you like to order?";
      return new Outcome(
          reply,
          "fixture-only",
          "fixture-response",
          21L,
          8L,
          false,
          null,
          false,
          false,
          0);
    }
  }

  private static final String VALID_EXPLANATION = """
      {"simpleEnglish":"What do you want to order?","meaningVi":"Bạn muốn gọi món gì?"}
      """;

  private static final String CLEAN_REVIEW =
      """
{"summary":"Bạn đã gọi món rõ ràng.","strengths":["Dùng please để gọi món lịch sự."],
"goalAssessment":"Đã gọi món chính, chưa xác nhận đồ uống.","corrections":[],
"suggestions":[{"itemType":"PHRASE","expression":"May I have some tea?","meaningVi":"Cho tôi xin trà được không?",
"exampleEn":"May I have some tea, please?"}],"nextPractice":"Thử gọi thêm đồ uống và xác nhận đơn."}
""";

  @Test
  void feedbackRejectsAssistantSourceAndRetriesOnlyFailedResult() {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    assertEquals(
        409,
        assertThrows(ApiException.class, () -> service.generateFeedback(owner, id, false, false))
            .status);
    var sent = service.send(owner, id, new SendTextRequest("I want order soup.", "feedback-wrong"));
    var learner = sent.messages().get(1);
    var opening = sent.messages().getFirst();
    service.finish(owner, id);
    assertEquals(
        404, assertThrows(ApiException.class, () -> service.getFeedback(owner(), id)).status);
    String correction =
        """
        [{"sourceMessageId":"%s","itemType":"ERROR","category":"GRAMMAR","ruleCode":"VERB_FORM",
        "originalText":"want order","suggestedText":"want to order",
        "explanationVi":"Sau want, dùng to + động từ nguyên mẫu để nói điều bạn muốn làm."}]
        """;
    provider.feedbackOutput =
        CLEAN_REVIEW.replace(
            "\"corrections\":[]", "\"corrections\":" + correction.formatted(opening.id()));
    var failed = service.generateFeedback(owner, id, false, false);
    assertEquals("FEEDBACK_INVALID", failed.errorCode());
    assertEquals("FAILED", failed.status());
    assertNull(failed.result());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM feedback_items WHERE session_id=?", Integer.class, id));
    assertEquals("COMPLETED", service.get(owner, id).status());
    assertEquals(sent.messages(), service.get(owner, id).messages());

    provider.feedbackOutput =
        CLEAN_REVIEW.replace(
            "\"corrections\":[]", "\"corrections\":" + correction.formatted(learner.id()));
    var result = service.generateFeedback(owner, id, true, false);
    assertEquals("SUCCEEDED", result.status());
    assertEquals(2, result.attempts());
    assertEquals(learner.id(), result.result().corrections().getFirst().sourceMessageId());
    assertEquals("want to order", result.result().corrections().getFirst().suggestedText());
    assertEquals(1, result.suggestionIds().size());
    int calls = provider.calls.get();
    assertEquals(result, service.generateFeedback(owner, id, false, false));
    assertEquals(result, service.generateFeedback(owner, id, true, false));
    assertEquals(calls, provider.calls.get());
  }

  @Test
  void feedbackAllowsEmptyCorrectionsAndRejectsInvalidSourceCategoryAndShape() {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    var sent =
        service.send(
            owner, id, new SendTextRequest("I would like soup, please.", "feedback-clean"));
    service.finish(owner, id);
    var analysis = new vn.englishconversation.feedback.service.FeedbackAnalysis();
    assertThrows(
        IllegalArgumentException.class,
        () -> analysis.validate(CLEAN_REVIEW + "{}", sent.messages()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            analysis.validate(
                CLEAN_REVIEW.replace(
                    "\"strengths\":[\"Dùng please để gọi món lịch sự.\"]", "\"strengths\":false"),
                sent.messages()));
    String item =
        """
        [{"sourceMessageId":"%s","itemType":"%s","category":"%s","ruleCode":"%s",
        "originalText":"%s","suggestedText":"different text","explanationVi":"Giải thích."}]
        """;
    String source = sent.messages().get(1).id();
    long otherSession = Long.parseLong(service.create(owner, draft()).id());
    String foreignSource = service.send(owner, otherSession,
        new SendTextRequest("I would like soup, please.", "foreign-feedback-source"))
        .messages().get(1).id();
    for (String invalid :
        java.util.List.of(
            item.formatted(foreignSource, "ERROR", "GRAMMAR", "ARTICLE", "soup"),
            item.formatted(source, "ERROR", "GRAMMAR", "ARTICLE", "invented fragment"),
            item.formatted(source, "ERROR", "EXPRESSION", "POLITENESS", "soup"),
            item.formatted(source, "IMPROVEMENT", "GRAMMAR", "WORD_CHOICE", "soup"))) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              analysis.validate(
                  CLEAN_REVIEW.replace("\"corrections\":[]", "\"corrections\":" + invalid),
                  sent.messages()));
    }
    provider.feedbackOutput = CLEAN_REVIEW;
    var result = service.generateFeedback(owner, id, false, false);
    assertEquals("SUCCEEDED", result.status());
    assertTrue(result.result().corrections().isEmpty());
    assertEquals(result, service.getFeedback(owner, id));
  }

  private AccountPrincipal owner() {
    String email = "chat-" + java.util.UUID.randomUUID() + "@example.test";
    long user=Long.parseLong(accounts.register(email, "Fixture account secret only").id());
    return new AccountPrincipal(user, 0);
  }

  private CreatePracticeRequest draft() {
    return new CreatePracticeRequest(
        jdbc.queryForObject(
            "SELECT CAST(id AS CHAR) FROM scenarios WHERE code='CLUB_INTRO'", String.class),
        "A2",
        java.util.UUID.randomUUID().toString());
  }

  @Test
  void createsSnapshotReplaysAndEnforcesOwnershipHintAndTurnLimit() {
    var owner = owner();
    var input = draft();
    var opening = service.create(owner, input);
    long id = Long.parseLong(opening.id());
    int before = provider.calls.get();
    assertEquals(opening.id(), service.create(owner, input).id());
    assertEquals(before, provider.calls.get());
    assertEquals(1, opening.messages().size());
    String catalogTitle =
        jdbc.queryForObject(
            "SELECT title FROM scenarios WHERE id=?", String.class, input.scenarioId());
    try {
      jdbc.update(
          "UPDATE scenarios SET title='Updated fixture title' WHERE id=?", input.scenarioId());
      assertEquals(opening.title(), service.get(owner, id).title());
    } finally {
      jdbc.update("UPDATE scenarios SET title=? WHERE id=?", catalogTitle, input.scenarioId());
    }
    assertEquals(404, assertThrows(ApiException.class, () -> service.get(owner(), id)).status);
    assertEquals(
        404,
        assertThrows(
                ApiException.class,
                () -> service.send(owner(), id, new SendTextRequest("Hello", "cross-owner")))
            .status);
    var hint = service.hint(owner, id, new HintRequest("hint-initial"));
    assertNotNull(hint.hint());
    assertEquals(1, hint.messages().size());
    assertTrue(service.hint(owner, id, new HintRequest("hint-initial")).hintAlreadyConsumed());
    for (int n = 1; n <= 20; n++) {
      var request = new SendTextRequest("I would like soup.", "learner-" + n);
      var response = service.send(owner, id, request);
      assertEquals(n, response.learnerTurns());
      assertEquals(n * 2 + 1, response.messages().size());
      assertEquals(n * 2 + 1, service.send(owner, id, request).messages().size());
    }
    assertEquals(
        "REQUEST_ID_REUSED",
        assertThrows(
                ApiException.class,
                () ->
                    service.send(
                        owner, id, new SendTextRequest("A different sentence.", "learner-20")))
            .code);
    assertEquals(
        "TURN_LIMIT",
        assertThrows(
                ApiException.class,
                () ->
                    service.send(
                        owner, id, new SendTextRequest("May I order again?", "learner-21")))
            .code);
    assertEquals("COMPLETED", service.finish(owner, id).status());
    assertEquals(41, service.get(owner, id).messages().size());
    assertEquals(
        22,
        jdbc.queryForObject(
            """
SELECT COUNT(*) FROM ai_attempts a JOIN ai_requests r ON r.id=a.ai_request_id
WHERE r.session_id=? AND a.latency_ms IS NOT NULL AND a.input_tokens=21 AND a.estimated_cost IS NULL
""",
            Integer.class,
            id));
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "G62_CONTEXT_FIXTURE", matches = "true")
  void trimsOldContextAndKeepsLatestMessageAndStoredTranscript() {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    String earliest = "Earlier context. " + "a".repeat(700);
    service.send(owner, id, new SendTextRequest(earliest, "context-first"));
    for (int n = 0; n < 3; n++)
      service.send(owner, id, new SendTextRequest("Recent context. " + "b".repeat(700), "context-middle-" + n));
    var response = service.send(owner, id, new SendTextRequest("Latest input.", "context-latest"));
    var prompt = provider.lastPrompt;
    assertEquals("Latest input.", prompt.history().getLast().content());
    assertTrue(prompt.history().stream().noneMatch(line -> earliest.equals(line.content())));
    assertTrue(prompt.instruction().length() + prompt.history().stream()
        .mapToInt(line -> line.content().length()).sum() <= 2400);
    assertEquals(5, response.learnerTurns());
    assertTrue(response.messages().stream().anyMatch(line -> earliest.equals(line.content())));
  }

  @Test
  void timeoutKeepsLearnerTextAndRetryReusesTurn() {
    var owner = owner();
    var opening = service.create(owner, draft());
    long id = Long.parseLong(opening.id());
    provider.timeout = true;
    var text = new SendTextRequest("Soup, please.", "timeout-turn");
    var failed = service.send(owner, id, text);
    assertEquals("UNKNOWN", failed.operation().status());
    assertEquals(
        List.of("ASSISTANT", "LEARNER"),
        failed.messages().stream().map(MessageResponse::sender).toList());
    long operation = Long.parseLong(failed.operation().id());
    assertEquals(
        "UNKNOWN_ACK_REQUIRED",
        assertThrows(
                ApiException.class,
                () -> service.retry(owner, id, operation, new RetryRequest(false)))
            .code);
    var completed = service.retry(owner, id, operation, new RetryRequest(true));
    assertEquals(1, completed.learnerTurns());
    assertEquals(3, completed.messages().size());
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ai_attempts WHERE ai_request_id=?", Integer.class, operation));
    assertEquals(3, service.send(owner, id, text).messages().size());
  }

  @Test
  void concurrentCallsSerializeSessionAndEnforceGlobalQuotaBeforeDispatch() throws Exception {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    long second = Long.parseLong(service.create(owner, draft()).id());
    provider.entered = new CountDownLatch(1);
    provider.release = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var request = new SendTextRequest("Hello again.", "blocked-turn");
      var running = executor.submit(() -> service.send(owner, id, request));
      try {
        assertTrue(provider.entered.await(5, TimeUnit.SECONDS));
        assertEquals("RUNNING", service.send(owner, id, request).operation().status());
        assertEquals(
            "PRACTICE_BUSY",
            assertThrows(
                    ApiException.class,
                    () ->
                        service.send(
                            owner, id, new SendTextRequest("Another input.", "other-turn")))
                .code);
        assertEquals(
            "PRACTICE_BUSY",
            assertThrows(
                    ApiException.class,
                    () -> service.hint(owner, id, new HintRequest("blocked-hint")))
                .code);
        assertEquals(
            "AI_QUOTA",
            assertThrows(
                    ApiException.class,
                    () ->
                        service.send(
                            owner, second, new SendTextRequest("Other session.", "quota-turn")))
                .code);
        assertEquals(0, service.get(owner, second).learnerTurns());
      } finally {
        provider.release.countDown();
      }
      assertEquals(3, running.get(6, TimeUnit.SECONDS).messages().size());
    } finally {
      provider.entered = null;
      provider.release = null;
    }
  }
}
