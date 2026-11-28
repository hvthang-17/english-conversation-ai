package vn.englishconversation.admin;


import vn.englishconversation.admin.dto.AdminCatalog.ScenarioInput;
import vn.englishconversation.admin.dto.AdminCatalog.ScenarioVariant;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.conversation.dto.*;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class AdminIntegrationMysqlTest extends FullSystemMysqlFixture {

  @Test
  void adminPublishesOneSnapshotAndAggregatesLearningAndAiSeparately() throws Exception {
    var json=new tools.jackson.databind.ObjectMapper();
    String base="http://127.0.0.1:"+port+"/api/v1/";
    var admin=java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL)).build();
    var learner=java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL)).build();
    String password="Synthetic announcement password";
    var editor=accounts.register("publisher-"+java.util.UUID.randomUUID()+"@example.test",password);
    var a2=accounts.register("reader-a2-"+java.util.UUID.randomUUID()+"@example.test",password);
    var b1=accounts.register("reader-b1-"+java.util.UUID.randomUUID()+"@example.test",password);
    accounts.changeAccess(Long.parseLong(editor.id()),"ADMIN","ACTIVE");
    jdbc.update("UPDATE learner_profiles SET selected_level='B1' WHERE user_id=?",b1.id());
    assertEquals(200,post(admin,json,base,"auth/login",java.util.Map.of("email",editor.email(),"password",password),true).statusCode());
    assertEquals(200,post(learner,json,base,"auth/login",java.util.Map.of("email",a2.email(),"password",password),true).statusCode());
    String announcements="admin/announcements";
    var draft=java.util.Map.of("title","Thông báo fixture A2","body","Nội dung mẫu",
        "audienceRole","LEARNER","audienceLevel","A2","expiresAt",java.time.Instant.now().plusSeconds(86400).toString());
    var create=java.util.Map.of("draft",draft,"clientRequestId",java.util.UUID.randomUUID().toString());
    assertEquals(403,post(learner,json,base,announcements,create,true).statusCode());
    var invalidAudience=new java.util.HashMap<>(draft);
    invalidAudience.put("audienceRole","ADMIN");
    assertEquals(422,post(admin,json,base,announcements+"/preview",invalidAudience,true).statusCode());
    assertEquals(200,post(admin,json,base,announcements+"/preview",draft,true).statusCode());
    var created=post(admin,json,base,announcements,create,true);
    assertEquals(200,created.statusCode(),created.body());
    String id=json.readTree(created.body()).get("id").asString();
    assertEquals(id,json.readTree(post(admin,json,base,announcements,create,true).body()).get("id").asString());
    var revision=new java.util.HashMap<>(draft);
    revision.put("body","Nội dung mẫu đã rà");
    assertEquals(200,request(admin,json,base,announcements+"/"+id,"PUT",
        java.util.Map.of("draft",revision,"version",0),true).statusCode());
    assertEquals(409,post(admin,json,base,announcements+"/"+id+"/publish",java.util.Map.of("version",0),true).statusCode());
    var published=post(admin,json,base,announcements+"/"+id+"/publish",java.util.Map.of("version",1),true);
    assertEquals(200,published.statusCode(),published.body());
    assertEquals(200,post(admin,json,base,announcements+"/"+id+"/publish",java.util.Map.of("version",1),true).statusCode());
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE announcement_id=? AND user_id=?",Integer.class,id,a2.id()));
    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE announcement_id=? AND user_id IN (?,?)",Integer.class,id,b1.id(),editor.id()));
    jdbc.update("UPDATE learner_profiles SET selected_level='B1' WHERE user_id=?",a2.id());
    jdbc.update("UPDATE learner_profiles SET selected_level='A2' WHERE user_id=?",b1.id());
    String inbox="learning/progress/notifications";
    assertTrue(request(learner,json,base,inbox,"GET",null,false).body().contains("Thông báo fixture A2"));
    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE announcement_id=? AND user_id=?",Integer.class,id,b1.id()));
    jdbc.update("UPDATE announcements SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",id);
    assertFalse(request(learner,json,base,inbox,"GET",null,false).body().contains("Thông báo fixture A2"));
    var listed=json.readTree(request(admin,json,base,announcements,"GET",null,false).body()).get("items").values();
    assertEquals("EXPIRED",listed.stream().filter(item -> id.equals(item.get("id").asString()))
        .findFirst().orElseThrow().get("status").asString());
    assertEquals(200,post(admin,json,base,announcements+"/"+id+"/archive",null,true).statusCode());
    assertEquals(1,jdbc.queryForObject("""
        SELECT COUNT(*) FROM admin_audit_logs WHERE actor_user_id=? AND target_id=? AND target_type='ANNOUNCEMENT'
          AND action='ANNOUNCEMENT_PUBLISHED' AND safe_metadata IS NULL
        """,Integer.class,editor.id(),id));

    String endpoint="admin/metrics?days=1";
    var emptyFrom=java.time.LocalDateTime.of(2000,1,1,0,0);
    assertEquals(0,adminMetrics.learning(emptyFrom,emptyFrom.plusDays(1)).endedConversations());
    assertEquals(403,request(learner,json,base,endpoint,"GET",null,false).statusCode());
    var initial=request(admin,json,base,endpoint,"GET",null,false);
    assertEquals(200,initial.statusCode(),initial.body());
    var initialMetrics=json.readTree(initial.body());
    var before=initialMetrics.get("learning");
    jdbc.update("""
        INSERT INTO practice_sessions(user_id,scenario_id,client_request_id,request_fingerprint,selected_level,
          scenario_snapshot,status,started_at,ended_at,last_activity_at)
        SELECT ?,MIN(id),?,REPEAT('a',64),'A2',JSON_OBJECT('title','Fixture'),?,
          UTC_TIMESTAMP(6)-INTERVAL 1 HOUR,IF(?='ACTIVE',NULL,UTC_TIMESTAMP(6)),UTC_TIMESTAMP(6)
        FROM scenarios
        """,a2.id(),"metrics-complete","COMPLETED","COMPLETED");
    jdbc.update("""
        INSERT INTO practice_sessions(user_id,scenario_id,client_request_id,request_fingerprint,selected_level,
          scenario_snapshot,status,started_at,ended_at,last_activity_at)
        SELECT user_id,scenario_id,'metrics-abandoned',request_fingerprint,selected_level,scenario_snapshot,
          'ABANDONED',started_at,ended_at,last_activity_at FROM practice_sessions WHERE user_id=? AND client_request_id='metrics-complete'
        """,a2.id());
    jdbc.update("""
        INSERT INTO practice_sessions(user_id,scenario_id,client_request_id,request_fingerprint,selected_level,
          scenario_snapshot,status,started_at,last_activity_at)
        SELECT user_id,scenario_id,'metrics-active',request_fingerprint,selected_level,scenario_snapshot,
          'ACTIVE',started_at,last_activity_at FROM practice_sessions WHERE user_id=? AND client_request_id='metrics-complete'
        """,a2.id());
    jdbc.update("""
        INSERT INTO ai_requests(user_id,task_type,operation_key,request_fingerprint,status,prompt_version,created_at)
        VALUES (?,'CHAT','metrics-fixture',REPEAT('b',64),'SUCCEEDED','fixture',UTC_TIMESTAMP(6))
        """,a2.id());
    long aiId=jdbc.queryForObject("SELECT id FROM ai_requests WHERE user_id=? AND operation_key='metrics-fixture'",Long.class,a2.id());
    jdbc.update("""
        INSERT INTO conversation_turns(session_id,turn_no,client_request_id,request_fingerprint,kind,status,ai_request_id,created_at)
        SELECT id,1,'metrics-turn',REPEAT('c',64),'LEARNER_TEXT','SUCCEEDED',?,UTC_TIMESTAMP(6)
        FROM practice_sessions WHERE user_id=? AND client_request_id='metrics-complete'
        """,aiId,a2.id());
    jdbc.update("""
        INSERT INTO messages(session_id,turn_id,sender,content,input_type,created_at)
        SELECT session_id,id,'LEARNER','Synthetic text','TEXT',UTC_TIMESTAMP(6) FROM conversation_turns WHERE ai_request_id=?
        """,aiId);
    jdbc.update("""
        INSERT INTO ai_attempts(ai_request_id,attempt_no,provider,model_name,status,error_code,input_tokens,output_tokens,latency_ms,started_at)
        VALUES (?,1,'METRICS_FIXTURE','fixture','FAILED','AI_QUOTA',NULL,NULL,20,UTC_TIMESTAMP(6)),
               (?,2,'METRICS_FIXTURE','fixture','SUCCEEDED',NULL,25,12,40,UTC_TIMESTAMP(6))
        """,aiId,aiId);
    var response=request(admin,json,base,endpoint,"GET",null,false);
    assertEquals(200,response.statusCode(),response.body());
    var after=json.readTree(response.body());
    var learning=after.get("learning");
    assertEquals(before.get("completedConversations").asInt()+1,learning.get("completedConversations").asInt());
    assertEquals(before.get("endedConversations").asInt()+2,learning.get("endedConversations").asInt());
    assertEquals(before.get("learnerMessages").asInt()+1,learning.get("learnerMessages").asInt());
    assertEquals(initialMetrics.get("accounts").get("registeredInPeriod"),after.get("accounts").get("registeredInPeriod"));
    assertFalse(response.body().contains("Synthetic text"));
    var metric=after.get("ai").values().stream().filter(row -> "METRICS_FIXTURE".equals(row.get("provider").asString()))
        .findFirst().orElseThrow();
    assertEquals(2,metric.get("attempts").asInt());
    assertEquals(1,metric.get("retries").asInt());
    assertEquals(1,metric.get("failed").asInt());
    assertEquals(1,metric.get("quotaErrors").asInt());
    assertEquals(25,metric.get("inputTokens").asInt());
    assertEquals(30,metric.get("averageLatencyMs").asInt());
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

    String optionsPath = "admin/materials/LESSONS/lesson-catalog-options";
    assertEquals(403, request(learner, json, base, optionsPath, "GET", null, false).statusCode());
    var options = request(admin, json, base, optionsPath, "GET", null, false);
    assertEquals(200, options.statusCode(), options.body());
    var catalogOptions = json.readTree(options.body());
    assertFalse(catalogOptions.get("topics").isEmpty());
    assertTrue(catalogOptions.get("scenarios").values().stream()
        .anyMatch(item -> item.get("levels").values().stream().anyMatch(level -> level.asString().equals("B1"))));
    assertFalse(options.body().contains("aiInstruction"));

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
}
