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
      "GEMINI_CHAT_ENABLED=false",
      "app.pronunciation.fingerprint-key=synthetic-g8-fingerprint-test-only-value"
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
  @Autowired vn.englishconversation.admin.repository.AdminMetricsStore adminMetrics;

  @Test
  void supportReportsProtectSourcesAndPublishOnlyEligibleInboxItems() throws Exception {
    var json = new tools.jackson.databind.ObjectMapper();
    var base = "http://127.0.0.1:" + port + "/api/v1/";
    var clients = new java.net.http.HttpClient[4];
    var profile = new long[4];
    for (int i=0;i<clients.length;i++) {
      clients[i] = java.net.http.HttpClient.newBuilder()
          .cookieHandler(new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL)).build();
      var password = "Synthetic support password";
      var account = accounts.register("support-" + java.util.UUID.randomUUID() + "@example.test",password);
      profile[i] = Long.parseLong(account.id());
      if (i>=2) accounts.changeAccess(profile[i],"ADMIN","ACTIVE");
      assertEquals(200,post(clients[i],json,base,"auth/login",
          java.util.Map.of("email",account.email(),"password",password),true).statusCode());
    }
    var learner=clients[0];
    var other=clients[1];
    var admin=clients[2];
    var tickets="support/tickets";
    var adminTickets="admin/support/tickets";
    assertEquals(403,request(learner,json,base,adminTickets,"GET",null,false).statusCode());
    var ordinary = java.util.Map.of("kind","TECHNICAL","title","Lỗi nút phát","body","Không phát được.",
        "clientRequestId",java.util.UUID.randomUUID().toString());
    assertEquals(200,post(learner,json,base,tickets,ordinary,true).statusCode());
    var lesson = java.util.Map.of("type","LESSON","id",101);
    assertEquals(200,post(learner,json,base,tickets+"/preview",lesson,true).statusCode());
    var session = post(learner,json,base,"practice-sessions",draft(),true);
    assertEquals(200,session.statusCode(),session.body());
    var practice=json.readTree(session.body());
    var sessionId=practice.get("id").asString();
    var message=practice.get("messages").get(0).get("id").asString();
    jdbc.update("UPDATE messages SET content=? WHERE id=?","Contact fixture@example.test or call +84 912 345 678.",message);
    var source = java.util.Map.of("type","AI_MESSAGE","id",message);
    assertEquals(404,post(other,json,base,tickets+"/preview",source,true).statusCode());
    var preview=post(learner,json,base,tickets+"/preview",source,true);
    assertEquals(200,preview.statusCode(),preview.body());
    assertFalse(preview.body().contains("fixture@example.test"));
    assertFalse(preview.body().contains("345"));
    var form = new java.util.HashMap<String,Object>(ordinary);
    form.put("kind","CONTENT_REPORT");
    form.put("clientRequestId",java.util.UUID.randomUUID().toString());
    assertEquals(400,post(learner,json,base,tickets,form,true).statusCode());
    form.put("sources",List.of(source,lesson));
    assertEquals(400,post(learner,json,base,tickets,form,true).statusCode());
    form.put("sources",List.of(source));
    assertEquals(409,post(learner,json,base,tickets,form,true).statusCode());
    form.put("previewRevision",json.readTree(preview.body()).get("revision").asString());
    var created=post(learner,json,base,tickets,form,true);
    assertEquals(200,created.statusCode(),created.body());
    String id=json.readTree(created.body()).get("id").asString();
    assertEquals(id,json.readTree(post(learner,json,base,tickets,form,true).body()).get("id").asString());
    String thread=tickets+"/"+id+"/messages";
    assertEquals(404,request(other,json,base,thread,"GET",null,false).statusCode());
    var reply=java.util.Map.of("body","Đã tiếp nhận.","clientRequestId",java.util.UUID.randomUUID().toString());
    var operation=adminTickets+"/"+id;
    var secondAdmin=clients[3];
    assertEquals(404,request(admin,json,base,"practice-sessions/"+sessionId,"GET",null,false).statusCode());
    assertEquals(403,post(admin,json,base,operation+"/messages",reply,true).statusCode());
    var claims=List.of(admin,secondAdmin).stream().map(client -> CompletableFuture.supplyAsync(() -> {
      try { return request(client,json,base,operation+"/assignment","PATCH",java.util.Map.of(),true); }
      catch (Exception failure) { throw new CompletionException(failure); }
    })).toList();
    assertEquals(List.of(200,409),claims.stream().map(claim -> claim.join().statusCode()).sorted().toList());
    boolean firstWon=claims.getFirst().join().statusCode()==200;
    var previous=firstWon ? admin : secondAdmin;
    var assigned=firstWon ? secondAdmin : admin;
    long previousId=profile[firstWon ? 2 : 3];
    long assignedId=profile[firstWon ? 3 : 2];
    assertEquals(403,post(assigned,json,base,operation+"/messages",reply,true).statusCode());
    assertEquals(403,request(assigned,json,base,operation+"/status","PATCH",java.util.Map.of("status","IN_REVIEW"),true).statusCode());
    var reassignment=request(assigned,json,base,operation+"/assignment","PATCH",
        java.util.Map.of("adminId",assignedId,"expectedAdminId",previousId),true);
    assertEquals(200,reassignment.statusCode(),reassignment.body());
    assertEquals(403,post(previous,json,base,operation+"/messages",reply,true).statusCode());
    assertEquals(204,post(assigned,json,base,operation+"/messages",reply,true).statusCode());
    assertEquals(204,post(assigned,json,base,operation+"/messages",reply,true).statusCode());
    assertEquals(1,jdbc.queryForObject("""
        SELECT COUNT(*) FROM admin_audit_logs WHERE target_type='SUPPORT_TICKET' AND target_id=? AND actor_user_id=?
          AND action='SUPPORT_REPLIED' AND safe_metadata IS NULL
        """,Integer.class,id,assignedId));
    assertEquals(2,json.readTree(request(learner,json,base,thread,"GET",null,false).body()).get("total").asInt());

    assertEquals(204,request(learner,json,base,"learning/history/"+sessionId,"DELETE",null,true).statusCode());
    var detached=json.readTree(request(learner,json,base,tickets+"/"+id,"GET",null,false).body());
    assertTrue(detached.get("sourceRemoved").asBoolean());
    assertTrue(detached.get("excerpt").isNull());
    var status=adminTickets+"/"+id+"/status";
    assertEquals(409,request(assigned,json,base,status,"PATCH",java.util.Map.of("status","CLOSED"),true).statusCode());
    for (String state : List.of("IN_REVIEW","RESOLVED","CLOSED"))
      assertEquals(200,request(assigned,json,base,status,"PATCH",java.util.Map.of("status",state),true).statusCode());
    assertEquals(409,post(learner,json,base,thread,reply,true).statusCode());

    for (int i=0;i<2;i++) {
      jdbc.update("""
          INSERT INTO announcements(title,body,status,created_at,published_at,expires_at)
          VALUES (?,?,'PUBLISHED',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)-INTERVAL 2 DAY,TIMESTAMPADD(DAY,?,UTC_TIMESTAMP(6)))
          ""","Fixture "+i,"Thông báo mẫu",i==0 ? 1 : -1);
      jdbc.update("""
          INSERT INTO notifications(user_id,announcement_id,kind,dedupe_key,title,body,created_at)
          SELECT ?,id,'ANNOUNCEMENT',CONCAT('announcement:',id),title,body,UTC_TIMESTAMP(6)
          FROM announcements WHERE id=(SELECT MAX(id) FROM announcements)
          """,profile[0]);
    }
    String inbox="learning/progress/notifications";
    var items=json.readTree(request(learner,json,base,inbox,"GET",null,false).body());
    assertEquals(2,items.size());
    assertEquals(0,json.readTree(request(other,json,base,inbox,"GET",null,false).body()).size());
    var support=items.values().stream().filter(item -> "SUPPORT".equals(item.get("kind").asString())).findFirst().orElseThrow();
    assertEquals(id,support.get("supportTicketId").asString());
    var read=inbox+"/"+support.get("id").asString()+"/read";
    assertEquals(404,request(other,json,base,read,"PUT",null,true).statusCode());
    assertEquals(204,request(learner,json,base,read,"PUT",null,true).statusCode());
    assertTrue(json.readTree(request(learner,json,base,inbox,"GET",null,false).body()).values().stream()
        .filter(item -> "SUPPORT".equals(item.get("kind").asString())).findFirst().orElseThrow().get("read").asBoolean());
  }

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
  void spacedReviewsOwnTheQueueAndPersistOneSchedulePerRating() throws Exception {
    var browser = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var outsider = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var json = new tools.jackson.databind.ObjectMapper();
    var base = "http://127.0.0.1:" + port + "/api/v1/";
    var password = "Synthetic spaced review password";
    var account = accounts.register("spaced-" + java.util.UUID.randomUUID() + "@example.test", password);
    var different = accounts.register("other-" + java.util.UUID.randomUUID() + "@example.test", password);
    assertEquals(200, post(browser, json, base, "auth/login",
        java.util.Map.of("email", account.email(), "password", password), true).statusCode());
    assertEquals(200, post(outsider, json, base, "auth/login",
        java.util.Map.of("email", different.email(), "password", password), true).statusCode());

    long owner = Long.parseLong(account.id());
    jdbc.update("""
        INSERT INTO review_items(user_id,item_type,expression,meaning_vi,example_en,status,created_at,updated_at)
        SELECT ?,'VOCABULARY',expression,meaning_vi,example_en,'REMEMBERED','2026-10-01','2026-10-01'
        FROM language_items ORDER BY id LIMIT 11
        """, owner);
    var cardIds = jdbc.queryForList("SELECT id FROM review_items WHERE user_id=? ORDER BY id", Long.class, owner);
    String queue = "learning/notebook/spaced";
    var firstPage = json.readTree(request(browser, json, base, queue, "GET", null, false).body());
    assertEquals(11, firstPage.get("newCount").asInt());
    assertEquals(10, firstPage.get("newEligibleCount").asInt());
    assertEquals("2026-11-28T00:00:00+07:00",
        java.time.Instant.parse(firstPage.get("nextLocalDayAt").asString())
            .atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime()
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    var items = firstPage.get("items");
    assertEquals(10, items.size());
    for (int i = 0; i < items.size(); i++)
      assertEquals(cardIds.get(i).toString(), items.get(i).get("card").get("id").asString());
    assertEquals(0, json.readTree(request(outsider, json, base, queue, "GET", null, false).body()).get("newCount").asInt());

    String attempts = "learning/notebook/" + cardIds.getFirst() + "/attempts";
    var grade = java.util.Map.of("mode", "SPACED_REVIEW", "rating", "GOOD", "scheduleVersion", 0, "clientRequestId", "first");
    assertEquals(404, post(outsider, json, base, attempts, grade, true).statusCode());
    assertEquals(403, post(browser, json, base, attempts, grade, false).statusCode());
    var otherTab = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    assertEquals(200, post(otherTab, json, base, "auth/login",
        java.util.Map.of("email", account.email(), "password", password), true).statusCode());
    var alternate = java.util.Map.of("mode", "SPACED_REVIEW", "rating", "GOOD", "scheduleVersion", 0, "clientRequestId", "another");
    var one = CompletableFuture.supplyAsync(() -> {
      try { return post(browser, json, base, attempts, grade, true); }
      catch (Exception failure) { throw new CompletionException(failure); }
    });
    var two = CompletableFuture.supplyAsync(() -> {
      try { return post(otherTab, json, base, attempts, alternate, true); }
      catch (Exception failure) { throw new CompletionException(failure); }
    });
    var left = one.join();
    var right = two.join();
    assertEquals(List.of(200, 409), java.util.stream.Stream.of(left, right).map(r -> r.statusCode()).sorted().toList());
    var saved = json.readTree((left.statusCode() == 200 ? left : right).body());
    assertTrue((left.statusCode() == 409 ? left : right).body().contains("REVIEW_SCHEDULE_CHANGED"));
    assertEquals("2026-11-30T02:00:00Z", saved.get("nextReviewAt").asString());
    assertEquals("REMEMBERED", saved.get("result").asString());
    String acceptedKey = left.statusCode() == 200 ? "first" : "another";
    assertEquals(saved, json.readTree(post(browser, json, base, attempts,
        left.statusCode() == 200 ? grade : alternate, true).body()));
    var changed = post(browser, json, base, attempts, java.util.Map.of(
        "mode", "SPACED_REVIEW", "rating", "EASY", "scheduleVersion", 0, "clientRequestId", acceptedKey), true);
    assertEquals(409, changed.statusCode());
    assertTrue(changed.body().contains("REQUEST_ID_REUSED"));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM review_attempts WHERE review_item_id=?", Integer.class, cardIds.getFirst()));
    var early = post(browser, json, base, attempts, java.util.Map.of(
        "mode", "SPACED_REVIEW", "rating", "HARD", "scheduleVersion", 1, "clientRequestId", "early"), true);
    assertEquals(409, early.statusCode());
    assertTrue(early.body().contains("REVIEW_NOT_DUE"));

    assertEquals(200, post(browser, json, base, attempts, java.util.Map.of(
        "mode", "FLASHCARD", "result", "NEEDS_REVIEW", "clientRequestId", "manual"), true).statusCode());
    assertEquals(1L, jdbc.queryForObject("SELECT schedule_version FROM review_items WHERE id=?", Long.class, cardIds.getFirst()));
    for (int i = 1; i < 10; i++) {
      assertEquals(200, post(browser, json, base, "learning/notebook/" + cardIds.get(i) + "/attempts", grade, true).statusCode());
    }
    var capped = post(browser, json, base, "learning/notebook/" + cardIds.getLast() + "/attempts", grade, true);
    assertEquals(409, capped.statusCode());
    assertTrue(capped.body().contains("NEW_REVIEW_LIMIT_REACHED"));

    jdbc.update("UPDATE review_items SET next_review_at='2026-11-27 02:00:00' WHERE id=?", cardIds.getFirst());
    var due = json.readTree(request(browser, json, base, queue, "GET", null, false).body());
    assertEquals(1, due.get("dueCount").asInt());
    assertEquals(0, due.get("newRemainingToday").asInt());
    var relearn = post(browser, json, base, attempts, java.util.Map.of(
        "mode", "SPACED_REVIEW", "rating", "AGAIN", "scheduleVersion", 1, "clientRequestId", "relearn"), true);
    assertEquals(200, relearn.statusCode(), relearn.body());
    assertEquals("2026-11-27T02:10:00Z", json.readTree(relearn.body()).get("nextReviewAt").asString());
    assertEquals(saved, json.readTree(post(browser, json, base, attempts,
        left.statusCode() == 200 ? grade : alternate, true).body()));

    jdbc.update("UPDATE review_items SET first_spaced_reviewed_at='2026-11-26 17:00:00' WHERE id=?", cardIds.get(1));
    assertEquals(0, json.readTree(request(browser, json, base, queue, "GET", null, false).body())
        .get("newRemainingToday").asInt());
    jdbc.update("UPDATE review_items SET first_spaced_reviewed_at='2026-11-26 16:59:59' WHERE id=?", cardIds.get(1));
    assertEquals(1, json.readTree(request(browser, json, base, queue, "GET", null, false).body())
        .get("newRemainingToday").asInt());
    jdbc.update("""
        INSERT INTO practice_schedules(user_id, local_time, time_zone, created_at, updated_at)
        VALUES (?,'08:00:00','America/Los_Angeles',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        ON DUPLICATE KEY UPDATE time_zone='America/Los_Angeles'
        """, owner);
    var shifted = json.readTree(request(browser, json, base, queue, "GET", null, false).body());
    assertEquals("America/Los_Angeles", shifted.get("timeZone").asString());
    assertEquals("2026-11-27T08:00:00Z", shifted.get("nextLocalDayAt").asString());
    assertEquals("2026-11-27T02:10:00.000000Z", shifted.get("nextDueAt").asString());
    assertEquals(204, request(browser, json, base, "learning/data", "DELETE", null, true).statusCode());
    assertEquals(404, post(browser, json, base, attempts, grade, true).statusCode());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM review_attempts WHERE review_item_id=?", Integer.class, cardIds.getFirst()));
  }

  @BeforeEach
  void ageAttemptsFromPreviousTestsOutOfTheQuotaWindow() {
    jdbc.update("""
        UPDATE ai_attempts
        SET started_at=started_at-INTERVAL 61 SECOND,finished_at=finished_at-INTERVAL 61 SECOND
        WHERE status<>'RUNNING' AND started_at>=UTC_TIMESTAMP(6)-INTERVAL 60 SECOND
        """);
  }

  @Autowired vn.englishconversation.mail.job.AccountMailWorker reminderWorker;
  @Autowired vn.englishconversation.pronunciation.service.PronunciationService pronunciation;

  @Test
  void progressRecordsCompletionMergesIntervalsAndOwnsInbox() throws Exception {
    var browser = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var outsider = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var json = new tools.jackson.databind.ObjectMapper();
    var base = "http://127.0.0.1:" + port + "/api/v1/";
    var path = "learning/progress";
    var password = "Synthetic G8 password";
    var account = accounts.register("progress-" + java.util.UUID.randomUUID() + "@example.test", password);
    var different = accounts.register("outsider-" + java.util.UUID.randomUUID() + "@example.test", password);
    long owner = Long.parseLong(account.id());
    assertEquals(200, post(browser,json,base,"auth/login",java.util.Map.of("email",account.email(),"password",password),true).statusCode());
    assertEquals(200, post(outsider,json,base,"auth/login",java.util.Map.of("email",different.email(),"password",password),true).statusCode());
    assertEquals(422, post(browser,json,base,path+"/lessons/101",null,true).statusCode());
    var drillIds = jdbc.queryForList("SELECT id FROM pronunciation_drills WHERE lesson_id=101 AND status='ACTIVE'", Long.class);
    String exerciseStatus = jdbc.queryForObject("SELECT status FROM listening_exercises WHERE id=201",String.class);
    try {
      for (long drill : drillIds) jdbc.update("UPDATE pronunciation_drills SET status='DRAFT' WHERE id=?",drill);
      jdbc.update("UPDATE listening_exercises SET status='DRAFT' WHERE id=201");
      var otherTab = java.net.http.HttpClient.newBuilder()
          .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
      assertEquals(200, post(otherTab,json,base,"auth/login",java.util.Map.of("email",account.email(),"password",password),true).statusCode());
      var left = CompletableFuture.supplyAsync(() -> progressPostStatus(browser,json,base,path+"/lessons/101"));
      var right = CompletableFuture.supplyAsync(() -> progressPostStatus(otherTab,json,base,path+"/lessons/101"));
      assertEquals(200,left.join());
      assertEquals(200,right.join());
      assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM lesson_completions WHERE user_id=?",Integer.class,owner));
      jdbc.update("UPDATE listening_exercises SET status='ACTIVE' WHERE id=202");
      var opened = post(browser,json,base,"listening/exercises/202/attempts",java.util.Map.of("clientRequestId","g8-listening"),true);
      assertEquals(200,opened.statusCode(),opened.body());
      String attempt = json.readTree(opened.body()).get("id").asString();
      var submitted = post(browser,json,base,"listening/attempts/"+attempt+"/submit",
          java.util.Map.of("draftVersion",0,"answers",java.util.Map.of()),true);
      assertEquals(200,submitted.statusCode(),submitted.body());
      assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM lesson_completions WHERE user_id=?",Integer.class,owner));
      var pulse = java.util.Map.of("eventId",java.util.UUID.randomUUID(),"streamId",java.util.UUID.randomUUID(),"activity","LESSON");
      assertEquals(204,post(browser,json,base,path+"/heartbeat",pulse,true).statusCode());
      assertEquals(204,post(browser,json,base,path+"/heartbeat",pulse,true).statusCode());
      assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM study_intervals WHERE user_id=?",Integer.class,owner));
      var dashboard = request(browser,json,base,path+"?level=A2","GET",null,false);
      assertEquals(200,dashboard.statusCode(),dashboard.body());
      var data = json.readTree(dashboard.body());
      assertEquals(20,data.get("points").asInt());
      assertEquals(0,data.get("listening").get(0).get("score").asInt());
      assertTrue(data.get("badges").toString().contains("FIRST_LESSON"));
      jdbc.update("UPDATE lessons SET status='ARCHIVED' WHERE id=101");
      try {
        var hidden = json.readTree(request(browser,json,base,path+"?level=A2","GET",null,false).body());
        assertTrue(hidden.get("recentCompletions").values().stream()
            .anyMatch(item -> item.get("id").asString().equals("101") && !item.get("available").asBoolean()));
        assertTrue(hidden.get("topics").values().stream().anyMatch(topic -> topic.get("total").asInt()==0));
      } finally {
        jdbc.update("UPDATE lessons SET status='ACTIVE' WHERE id=101");
      }
      var score = java.math.BigDecimal.valueOf(80);
      org.mockito.Mockito.when(pronunciationProvider.available()).thenReturn(true);
      org.mockito.Mockito.when(pronunciationProvider.assess(org.mockito.ArgumentMatchers.any(),
          org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("en-US")))
          .thenReturn(new vn.englishconversation.pronunciation.dto.PronunciationData.Assessment(
              "Go straight.",score,score,score,score,List.of()));
      long drill = jdbc.queryForObject("SELECT MIN(id) FROM pronunciation_drills WHERE lesson_id=104",Long.class);
      var pronounced = pronunciation.submit(owner,drill,"g8-scripted-attempt-1",wave(16000,123,1));
      var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
      while (!"SUCCEEDED".equals(pronunciation.get(owner,pronounced.id()).status()) && System.nanoTime()<deadline)
        Thread.sleep(100);
      assertEquals("SUCCEEDED",pronunciation.get(owner,pronounced.id()).status());
      assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM lesson_completions WHERE user_id=? AND lesson_id=104",Integer.class,owner));

      for (int lesson : new int[]{103,105}) jdbc.update("""
          INSERT INTO lesson_completions VALUES(?,?,UTC_TIMESTAMP(6),'fixture')
          """, owner,lesson);
      assertEquals(200, post(browser,json,base,path+"/lessons/101",null,true).statusCode());
      var vietnam = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
      var today = java.time.LocalDate.now(vietnam);
      for (int day=1;day<=3;day++) {
        var start = today.minusDays(day).atTime(12,0).atZone(vietnam).toInstant();
        for (int segment=0;segment<2;segment++) {
          jdbc.update("""
              INSERT INTO study_intervals(user_id,client_event_id,stream_id,started_at,ended_at,activity_kind)
              VALUES(?,?,?,?,?,'LESSON')
              """,owner,java.util.UUID.randomUUID().toString(),java.util.UUID.randomUUID().toString(),
              java.sql.Timestamp.from(start.plusSeconds(segment*30L)),
              java.sql.Timestamp.from(start.plusSeconds((segment+1)*30L)));
        }
      }
      assertEquals(204, post(browser,json,base,path+"/heartbeat",
          java.util.Map.of("eventId",java.util.UUID.randomUUID(),"streamId",java.util.UUID.randomUUID(),"activity","LESSON"),true).statusCode());
      var awards = json.readTree(request(browser,json,base,path,"GET",null,false).body());
      assertEquals(50,awards.get("points").asInt());
      assertEquals(3,awards.get("streak").asInt());
      assertEquals(3,awards.get("badges").size());
      var notifications = json.readTree(request(browser,json,base,path+"/notifications","GET",null,false).body());
      String notification = path+"/notifications/"+notifications.get(0).get("id").asString()+"/read";
      assertEquals(404,request(outsider,json,base,notification,"PUT",null,true).statusCode());
      assertEquals(204,request(browser,json,base,notification,"PUT",null,true).statusCode());
      assertTrue(json.readTree(request(browser,json,base,path+"/notifications","GET",null,false).body()).get(0).get("read").asBoolean());

      java.time.Instant now = java.time.Instant.now();
      var local = now.atZone(java.time.ZoneOffset.UTC);
      jdbc.update("""
          INSERT INTO practice_schedules(user_id,local_time,time_zone,enabled,email_reminder_enabled,created_at,updated_at)
          VALUES (?,?,'UTC',TRUE,FALSE,UTC_TIMESTAMP(6)-INTERVAL 1 DAY,UTC_TIMESTAMP(6)-INTERVAL 1 DAY)
          """,owner,java.sql.Time.valueOf(local.toLocalTime()));
      jdbc.update("""
          INSERT INTO practice_schedule_days SELECT id,? FROM practice_schedules WHERE user_id=?
          """,local.getDayOfWeek().getValue(),owner);
      reminderWorker.generate(now.plusSeconds(1));
      reminderWorker.generate(now.plusSeconds(1));
      assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND kind='REMINDER'",Integer.class,owner));
      assertEquals(204, request(browser,json,base,"learning/data","DELETE",null,true).statusCode());
      var cleared = json.readTree(request(browser,json,base,path,"GET",null,false).body());
      assertEquals(0,cleared.get("points").asInt());
      assertEquals(0,cleared.get("badges").size());
      assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM study_intervals WHERE user_id=?",Integer.class,owner));
    } finally {
      for (long drill : drillIds) jdbc.update("UPDATE pronunciation_drills SET status='ACTIVE' WHERE id=?",drill);
      jdbc.update("UPDATE listening_exercises SET status=? WHERE id=201",exerciseStatus);
      jdbc.update("UPDATE listening_exercises SET status='DRAFT' WHERE id=202");
    }
  }

  @Test
  @EnabledIfEnvironmentVariable(named="G8_MAILPIT_TEST",matches="true")
  void reminderWorkersSendOnceThroughMailpit() throws Exception {
    var account = accounts.register("g8-reminder-" + java.util.UUID.randomUUID() + "@example.test",
        "Synthetic G8 reminder password");
    long owner = Long.parseLong(account.id());
    var now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1);
    jdbc.update("""
        INSERT INTO practice_schedules(user_id,local_time,time_zone,enabled,email_reminder_enabled,created_at,updated_at)
        VALUES (?,?,'UTC',TRUE,TRUE,UTC_TIMESTAMP(6)-INTERVAL 1 DAY,UTC_TIMESTAMP(6)-INTERVAL 1 DAY)
        """, owner,java.sql.Time.valueOf(now.toLocalTime()));
    jdbc.update("INSERT INTO practice_schedule_days SELECT id,? FROM practice_schedules WHERE user_id=?",
        now.getDayOfWeek().getValue(),owner);
    var a = CompletableFuture.runAsync(reminderWorker::tick);
    var b = CompletableFuture.runAsync(reminderWorker::tick);
    CompletableFuture.allOf(a,b).get(20, TimeUnit.SECONDS);
    assertEquals(1,jdbc.queryForObject("""
        SELECT COUNT(*) FROM practice_reminder_deliveries d JOIN practice_schedules s ON s.id=d.schedule_id
        WHERE s.user_id=? AND d.status='SENT' AND d.attempt_count=1
        """,Integer.class,owner));
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND kind='REMINDER'",Integer.class,owner));
    var mailbox = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
        java.net.URI.create("http://127.0.0.1:18025/api/v1/messages")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(200,mailbox.statusCode());
    assertEquals(1,new tools.jackson.databind.ObjectMapper().readTree(mailbox.body()).get("messages").size());
    assertTrue(mailbox.body().contains(account.email()));
  }

  @Test
  void reminderRejectsStaleSchedulesAndDoesNotRetryAmbiguousDelivery(
      @Autowired vn.englishconversation.common.repository.JdbcStore store,
      @Autowired vn.englishconversation.auth.security.TokenCipher cipher,
      @Autowired org.springframework.transaction.PlatformTransactionManager manager,
      @Autowired org.springframework.core.env.Environment environment) {
    var mail = org.mockito.Mockito.mock(vn.englishconversation.mail.provider.MailGateway.class);
    org.mockito.Mockito.when(mail.available()).thenReturn(true);
    var worker = new vn.englishconversation.mail.job.AccountMailWorker(store,jdbc,mail,cipher,lifecycle,manager,environment,true);
    for (String scenario : List.of("connect","timeout","stale","locked","deleting")) {
      var account = accounts.register(scenario + "-reminder-" + java.util.UUID.randomUUID() + "@example.test",
          "Synthetic notification password");
      long owner = Long.parseLong(account.id());
      if (scenario.equals("locked")) jdbc.update("UPDATE users SET status='LOCKED' WHERE id=?",owner);
      if (scenario.equals("deleting")) jdbc.update("UPDATE users SET deletion_requested_at=UTC_TIMESTAMP(6) WHERE id=?",owner);
      jdbc.update("""
          INSERT INTO practice_schedules(user_id,local_time,time_zone,enabled,email_reminder_enabled,version,created_at,updated_at)
          VALUES (?,'12:00:00','UTC',TRUE,TRUE,2,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
          """,owner);
      jdbc.update("""
          INSERT INTO practice_reminder_deliveries(schedule_id,schedule_version,scheduled_for,expires_at,next_attempt_at,created_at)
          SELECT id,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 30 MINUTE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
          FROM practice_schedules WHERE user_id=?
          """,scenario.equals("stale") ? 1 : 2,owner);
      if (scenario.equals("connect") || scenario.equals("timeout")) {
        var failure = scenario.equals("connect") ? new java.net.ConnectException() : new java.net.SocketTimeoutException();
        var messageFailure = new jakarta.mail.MessagingException("Fixture transport", failure);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException(
                "Fixture transport", null, java.util.Map.of("message", messageFailure)))
            .when(mail).send(org.mockito.ArgumentMatchers.eq(account.email()),org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
      }
      worker.tick();
      String expected = switch (scenario) { case "connect" -> "RETRY"; case "timeout" -> "UNKNOWN"; default -> "CANCELLED"; };
      assertEquals(expected,jdbc.queryForObject("""
          SELECT d.status FROM practice_reminder_deliveries d JOIN practice_schedules s ON s.id=d.schedule_id
          WHERE s.user_id=?
          """,String.class,owner));
      if (expected.equals("CANCELLED")) org.mockito.Mockito.verify(mail,org.mockito.Mockito.never())
          .send(org.mockito.ArgumentMatchers.eq(account.email()),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
      if (scenario.equals("connect") || scenario.equals("timeout")) {
        jdbc.update("""
            UPDATE practice_reminder_deliveries d JOIN practice_schedules s ON s.id=d.schedule_id
            SET d.next_attempt_at=UTC_TIMESTAMP(6) WHERE s.user_id=?
            """,owner);
        worker.tick();
        org.mockito.Mockito.verify(mail, org.mockito.Mockito.times(scenario.equals("connect") ? 2 : 1))
            .send(org.mockito.ArgumentMatchers.eq(account.email()),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
      }
      jdbc.update("DELETE FROM practice_schedules WHERE user_id=?",owner);
    }
  }

  private int progressPostStatus(java.net.http.HttpClient browser, tools.jackson.databind.ObjectMapper json,
      String base, String path) {
    try { return post(browser,json,base,path,null,true).statusCode(); }
    catch (Exception failure) { throw new CompletionException(failure); }
  }

  @Test
  void learningPlanGuardsVersionsSourcesAndDeletion() throws Exception {
    var browser = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var second = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var json = new tools.jackson.databind.ObjectMapper();
    String base = "http://127.0.0.1:" + port + "/api/v1/";
    String plans = "learning/plans";
    String password = "Synthetic planner password";
    var a = accounts.register("plan-a-" + java.util.UUID.randomUUID() + "@example.test", password);
    var b = accounts.register("plan-b-" + java.util.UUID.randomUUID() + "@example.test", password);
    assertEquals(200, post(browser, json, base, "auth/login",
        java.util.Map.of("email", a.email(), "password", password), true).statusCode());
    assertEquals(200, post(second, json, base, "auth/login",
        java.util.Map.of("email", b.email(), "password", password), true).statusCode());
    var settings = java.util.Map.of("goal", "Practice introductions", "focusSkill", "CONVERSATION",
        "startDate", "2026-12-01", "days", 7);
    var create = java.util.Map.of("settings", settings);
    var made = post(browser, json, base, plans, create, true);
    assertEquals(201, made.statusCode(), made.body());
    var plan = json.readTree(made.body()).get("plan");
    String path = plans + "/" + plan.get("id").asString();
    assertEquals("2026-12-07", plan.get("endDate").asString());
    assertEquals(409, post(browser, json, base, plans, create, true).statusCode());
    assertEquals(404, request(second, json, base, path, "GET", null, false).statusCode());

    long aId = Long.parseLong(a.id());
    long bId = Long.parseLong(b.id());
    for (long owner : List.of(aId, bId)) {
      jdbc.update("""
          INSERT INTO review_items(user_id,item_type,expression,meaning_vi,status,created_at,updated_at)
          VALUES (?,'PHRASE','Hello again','Chào lại','NEEDS_REVIEW',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
          """, owner);
    }
    long ownedCard = jdbc.queryForObject("SELECT id FROM review_items WHERE user_id=?", Long.class, aId);
    long otherCard = jdbc.queryForObject("SELECT id FROM review_items WHERE user_id=?", Long.class, bId);
    var exactCard = request(browser, json, base, "learning/notebook/" + ownedCard, "GET", null, false);
    assertEquals(200, exactCard.statusCode(), exactCard.body());
    assertEquals(Long.toString(ownedCard), json.readTree(exactCard.body()).get("id").asString());
    assertEquals(404, request(second, json, base, "learning/notebook/" + ownedCard, "GET", null, false).statusCode());
    var lesson = java.util.Map.of("type", "LESSON", "id", 101);
    assertEquals(422, post(browser, json, base, path + "/items",
        java.util.Map.of("version", 1, "dueDate", "2026-12-08", "position", 1, "source", lesson), true).statusCode());
    assertEquals(404, post(browser, json, base, path + "/items", java.util.Map.of("version", 1,
        "dueDate", "2026-12-01", "position", 1, "source", java.util.Map.of("type", "REVIEW_ITEM", "id", otherCard)), true).statusCode());
    var added = post(browser, json, base, path + "/items", java.util.Map.of("version", 1,
        "dueDate", "2026-12-01", "position", 1, "source", java.util.Map.of("type", "REVIEW_ITEM", "id", ownedCard),
        "recommended", true), true);
    assertEquals(201, added.statusCode(), added.body());
    var item = json.readTree(added.body()).get("items").get(0);
    assertEquals("REVIEW_NEEDED", item.get("reasonCode").asString());
    String itemPath = path + "/items/" + item.get("id").asString();
    assertEquals(409, request(browser, json, base, path, "PUT",
        java.util.Map.of("version", 1, "settings", settings), true).statusCode());

    assertEquals(204, request(browser, json, base, "learning/notebook/" + ownedCard, "DELETE", null, true).statusCode());
    var lost = json.readTree(request(browser, json, base, path, "GET", null, false).body()).get("items").get(0);
    assertTrue(lost.get("reviewItemId").isNull());
    assertFalse(lost.get("available").asBoolean());
    assertEquals("Hello again", lost.get("title").asString());
    assertEquals("PLANNED", lost.get("status").asString());
    var replaced = request(browser, json, base, itemPath, "PUT",
        java.util.Map.of("version", 2, "dueDate", "2026-12-02", "position", 1, "source", lesson), true);
    assertEquals(200, replaced.statusCode(), replaced.body());
    var result = post(browser, json, base, itemPath + "/finish",
        java.util.Map.of("version", 3, "result", "COMPLETED"), true);
    assertEquals(200, result.statusCode(), result.body());
    assertFalse(json.readTree(result.body()).get("items").get(0).get("completedAt").isNull());
    String oldStatus = jdbc.queryForObject("SELECT status FROM lessons WHERE id=101", String.class);
    try {
      jdbc.update("UPDATE lessons SET status='ARCHIVED' WHERE id=101");
      var hidden = json.readTree(request(browser, json, base, path, "GET", null, false).body()).get("items").get(0);
      assertFalse(hidden.get("available").asBoolean());
      assertEquals("COMPLETED", hidden.get("status").asString());
      assertFalse(hidden.get("title").asString().isBlank());
    } finally {
      jdbc.update("UPDATE lessons SET status=? WHERE id=101", oldStatus);
    }
    var successor = post(browser, json, base, plans, java.util.Map.of("settings", settings,
        "replaceId", plan.get("id").asLong(), "version", 4), true);
    assertEquals(201, successor.statusCode(), successor.body());
    assertEquals("ARCHIVED", json.readTree(request(browser, json, base, path, "GET", null, false).body())
        .get("plan").get("status").asString());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM learning_plans WHERE user_id=? AND status='ACTIVE'", Integer.class, aId));
    String nextPath = plans + "/" + json.readTree(successor.body()).get("plan").get("id").asString();
    var tab = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    assertEquals(200, post(tab, json, base, "auth/login",
        java.util.Map.of("email", a.email(), "password", password), true).statusCode());
    var sameVersion = java.util.Map.of("version", 1, "settings", settings);
    var left = CompletableFuture.supplyAsync(() -> planEditStatus(browser, json, base, nextPath, sameVersion));
    var right = CompletableFuture.supplyAsync(() -> planEditStatus(tab, json, base, nextPath, sameVersion));
    assertEquals(List.of(200, 409), java.util.stream.Stream.of(left.join(), right.join()).sorted().toList());
    var firstItem = post(browser, json, base, nextPath + "/items", java.util.Map.of("version", 2,
        "dueDate", "2026-12-01", "position", 1, "source", lesson), true);
    assertEquals(201, firstItem.statusCode(), firstItem.body());
    String firstId = json.readTree(firstItem.body()).get("items").get(0).get("id").asString();
    var secondItem = post(browser, json, base, nextPath + "/items", java.util.Map.of("version", 3,
        "dueDate", "2026-12-01", "position", 1, "source", java.util.Map.of("type", "LESSON", "id", 102)), true);
    assertEquals(201, secondItem.statusCode(), secondItem.body());
    assertEquals(firstId, json.readTree(secondItem.body()).get("items").get(1).get("id").asString());
    var reordered = request(browser, json, base, nextPath + "/items/" + firstId, "PUT",
        java.util.Map.of("version", 4, "dueDate", "2026-12-01", "position", 1), true);
    assertEquals(200, reordered.statusCode(), reordered.body());
    assertEquals(firstId, json.readTree(reordered.body()).get("items").get(0).get("id").asString());
    var skipped = post(browser, json, base, nextPath + "/items/" + firstId + "/finish",
        java.util.Map.of("version", 5, "result", "SKIPPED"), true);
    assertEquals(200, skipped.statusCode(), skipped.body());
    assertEquals("SKIPPED", json.readTree(skipped.body()).get("items").get(0).get("status").asString());
    assertEquals(204, request(browser, json, base, "learning/data", "DELETE", null, true).statusCode());
    assertEquals(0, json.readTree(request(browser, json, base, plans, "GET", null, false).body()).size());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM learning_plan_items WHERE plan_id=?", Integer.class, plan.get("id").asLong()));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM review_items WHERE id=?", Integer.class, otherCard));
  }

  private int planEditStatus(java.net.http.HttpClient browser, tools.jackson.databind.ObjectMapper json,
      String base, String path, Object body) {
    try {
      return request(browser, json, base, path, "PUT", body, true).statusCode();
    } catch (Exception failure) {
      throw new CompletionException(failure);
    }
  }

  @Test
  void difficultyRequiresThreeComparableExercisesAndUsesExactThresholds() throws Exception {
    var browser = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var json = new tools.jackson.databind.ObjectMapper();
    String base = "http://127.0.0.1:" + port + "/api/v1/";
    String path = "learning/recommendations/difficulty";
    assertEquals(401, request(browser, json, base, path, "GET", null, false).statusCode());
    String password = "Synthetic difficulty password";
    var account = accounts.register("difficulty-" + java.util.UUID.randomUUID() + "@example.test", password);
    long owner = Long.parseLong(account.id());
    assertEquals(200, post(browser, json, base, "auth/login",
        java.util.Map.of("email", account.email(), "password", password), true).statusCode());
    jdbc.update("UPDATE learner_profiles SET selected_level='A2' WHERE user_id=?", owner);
    var snapshots = jdbc.queryForList("""
        SELECT id,status,questions_json FROM listening_exercises WHERE id IN (201,202,204,203,205,206)
        """);
    String matching = java.util.Collections.nCopies(5, "{\"type\":\"DICTATION\"}").stream()
        .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    try {
      jdbc.update("""
          UPDATE listening_exercises SET status='ACTIVE',questions_json=?
          WHERE id IN (201,202,204,203,205,206)
          """, matching);
      saveListeningScore(owner, 201, "a-1", 4, 1);
      saveListeningScore(owner, 201, "a-2", 4, 1);
      saveListeningScore(owner, 202, "a-3", 4, 1);
      assertTrue(difficultyResponse(browser, json, base, path).isNull());
      saveListeningScore(owner, 204, "a-4", 4, 2);
      assertTrue(difficultyResponse(browser, json, base, path).isNull());
      saveListeningScore(owner, 204, "a-5", 4, 1);
      jdbc.update("UPDATE listening_exercises SET questions_json=? WHERE id=204", matching.replace("DICTATION", "FILL_BLANK"));
      assertTrue(difficultyResponse(browser, json, base, path).isNull());
      jdbc.update("UPDATE listening_exercises SET questions_json=? WHERE id=204", matching);
      assertEquals("B1", difficultyResponse(browser, json, base, path).get("suggestedLevel").asString());
      assertEquals("A2", jdbc.queryForObject("SELECT selected_level FROM learner_profiles WHERE user_id=?", String.class, owner));

      jdbc.update("UPDATE learner_profiles SET selected_level='B1' WHERE user_id=?", owner);
      saveListeningScore(owner, 203, "b-1", 2, 1);
      saveListeningScore(owner, 205, "b-2", 2, 1);
      saveListeningScore(owner, 206, "b-3", 3, 1);
      assertTrue(difficultyResponse(browser, json, base, path).isNull());
      saveListeningScore(owner, 206, "b-4", 2, 1);
      assertEquals("A2", difficultyResponse(browser, json, base, path).get("suggestedLevel").asString());
      assertEquals(204, request(browser, json, base, "learning/data", "DELETE", null, true).statusCode());
      assertTrue(difficultyResponse(browser, json, base, path).isNull());
      assertEquals("B1", jdbc.queryForObject("SELECT selected_level FROM learner_profiles WHERE user_id=?", String.class, owner));
    } finally {
      for (var row : snapshots) {
        jdbc.update("UPDATE listening_exercises SET status=?,questions_json=? WHERE id=?",
            row.get("status"), row.get("questions_json"), row.get("id"));
      }
    }
  }

  private void saveListeningScore(long owner, int exercise, String key, int score, int gradingVersion) {
    jdbc.update("""
        INSERT INTO listening_attempts(user_id,exercise_id,client_request_id,status,score,max_score,
          answers_json,started_at,submitted_at,grading_version)
        VALUES(?,?,?,'SUBMITTED',?,5,JSON_ARRAY(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?)
        """, owner, exercise, key, score, gradingVersion);
  }

  private tools.jackson.databind.JsonNode difficultyResponse(java.net.http.HttpClient browser,
      tools.jackson.databind.ObjectMapper json, String base, String path) throws Exception {
    var response = request(browser, json, base, path, "GET", null, false);
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).get("suggestion");
  }

  @Test
  void recommendationsUseOwnedEvidenceAndAvailableCatalogInStableOrder() throws Exception {
    var json = new tools.jackson.databind.ObjectMapper();
    String base = "http://127.0.0.1:" + port + "/api/v1/";
    String path = "learning/recommendations";
    var first = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var second = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    assertEquals(401, request(first, json, base, path, "GET", null, false).statusCode());
    String suffix = java.util.UUID.randomUUID().toString();
    String password = "Synthetic recommendation password";
    var a = accounts.register("recommend-a-" + suffix + "@example.test", password);
    var b = accounts.register("recommend-b-" + suffix + "@example.test", password);
    long owner = Long.parseLong(a.id());
    long other = Long.parseLong(b.id());
    assertEquals(200, post(first, json, base, "auth/login",
        java.util.Map.of("email", a.email(), "password", password), true).statusCode());
    assertEquals(200, post(second, json, base, "auth/login",
        java.util.Map.of("email", b.email(), "password", password), true).statusCode());
    jdbc.update("UPDATE learner_profiles SET selected_level='A2' WHERE user_id IN (?,?)", owner, other);
    var fresh = request(first, json, base, path, "GET", null, false);
    assertEquals(200, fresh.statusCode(), fresh.body());
    assertEquals("LEVEL_MATCH", json.readTree(fresh.body()).get(0).get("reasonCode").asString());
    for (var item : json.readTree(fresh.body())) assertEquals("A2", item.get("level").asString());

    jdbc.update("""
        INSERT INTO user_topic_interests(user_id,topic_id,created_at)
        SELECT ?,topic_id,UTC_TIMESTAMP(6) FROM lessons WHERE id=104
        """, owner);
    jdbc.update("""
        INSERT INTO user_topic_interests(user_id,topic_id,created_at)
        SELECT ?,topic_id,UTC_TIMESTAMP(6) FROM lessons WHERE id=102
        """, other);
    var aTopics = json.readTree(request(first, json, base, path, "GET", null, false).body());
    var bTopics = json.readTree(request(second, json, base, path, "GET", null, false).body());
    assertEquals("104", aTopics.get(0).get("id").asString());
    assertEquals("102", bTopics.get(0).get("id").asString());
    assertEquals("TOPIC_INTEREST", aTopics.get(0).get("reasonCode").asString());
    String focused = request(first, json, base, path + "?focus=PRONUNCIATION", "GET", null, false).body();
    assertTrue(focused.contains("FOCUS_MATCH"), focused);

    for (var card : List.of("Needs practice", "Already remembered")) {
      jdbc.update("""
          INSERT INTO review_items(user_id,item_type,expression,meaning_vi,status,created_at,updated_at)
          VALUES (?,'PHRASE',?,'Mẫu thử',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
          """, owner, card, card.equals("Needs practice") ? "NEEDS_REVIEW" : "REMEMBERED");
    }
    String oldExercise = jdbc.queryForObject("SELECT status FROM listening_exercises WHERE id=201", String.class);
    String oldLesson = jdbc.queryForObject("SELECT status FROM lessons WHERE id=102", String.class);
    try {
      jdbc.update("UPDATE listening_exercises SET status='ACTIVE' WHERE id=201");
      jdbc.update("UPDATE lessons SET status='ARCHIVED' WHERE id=102");
      for (int i = 0; i < 2; i++) {
        jdbc.update("""
            INSERT INTO listening_attempts(user_id,exercise_id,client_request_id,status,score,max_score,
              answers_json,started_at,submitted_at)
            VALUES (?,201,?,'SUBMITTED',1,4,JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """, owner, "recommend-" + i);
      }
      var suggestions = request(first, json, base, path, "GET", null, false);
      assertEquals(200, suggestions.statusCode(), suggestions.body());
      var data = json.readTree(suggestions.body());
      assertEquals("REVIEW_NEEDED", data.get(0).get("reasonCode").asString());
      assertEquals("Needs practice", data.get(0).get("title").asString());
      assertEquals("SKILL_GAP", data.get(1).get("reasonCode").asString());
      assertEquals("101", data.get(1).get("id").asString());
      assertFalse(suggestions.body().contains("Already remembered"));
      assertFalse(suggestions.body().contains("\"id\":\"102\""));
      String otherBody = request(second, json, base, path, "GET", null, false).body();
      assertFalse(otherBody.contains("Needs practice"));
      assertFalse(otherBody.contains("SKILL_GAP"));
      assertEquals(suggestions.body(), request(first, json, base, path, "GET", null, false).body());
    } finally {
      jdbc.update("UPDATE listening_exercises SET status=? WHERE id=201", oldExercise);
      jdbc.update("UPDATE lessons SET status=? WHERE id=102", oldLesson);
    }
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

  @Test
  void clubIntroOffersBothRolesAndStartsInTheSelectedRole() throws Exception {
    var browser = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var json = new tools.jackson.databind.ObjectMapper();
    var base = "http://127.0.0.1:" + port + "/api/v1/";
    var password = "Synthetic club role password";
    var account = accounts.register("club-roles-" + java.util.UUID.randomUUID() + "@example.test", password);
    assertEquals(200, post(browser, json, base, "auth/login",
        java.util.Map.of("email", account.email(), "password", password), true).statusCode());
    String scenario = jdbc.queryForObject(
        "SELECT CAST(id AS CHAR) FROM scenarios WHERE code='CLUB_INTRO'", String.class);
    var catalog = request(browser, json, base, "scenarios/" + scenario + "?level=A2", "GET", null, false);
    assertEquals(200, catalog.statusCode());
    var roles = json.readTree(catalog.body()).get("roles");
    assertEquals(2, roles.size());
    assertEquals("Thành viên mới", roles.get(0).get("learnerRole").asString());
    assertEquals("Thành viên đón tiếp", roles.get(1).get("learnerRole").asString());
    assertEquals("Thành viên mới", roles.get(1).get("aiRole").asString());
    assertFalse(catalog.body().contains("Play a new club member"));

    var created = post(browser, json, base, "practice-sessions",
        new CreatePracticeRequest(scenario, "A2", "club-intro-reverse", "TEXT", "REVERSE"), true);
    assertEquals(200, created.statusCode(), created.body());
    var practice = json.readTree(created.body());
    assertEquals("REVERSE", practice.get("roleCode").asString());
    assertEquals("Thành viên đón tiếp", practice.get("learnerRole").asString());
    assertEquals("Thành viên mới", practice.get("aiRole").asString());
    assertTrue(provider.lastPrompt.instruction().contains("Play a new club member"));
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
  void aggregatesOnlyRepeatedErrorsAcrossDifferentMessages(
      @Autowired vn.englishconversation.learning.repository.ProgressRepository progress) {
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
    assertEquals(2, learning.statistics(owner).errorGroups().getFirst().count());
    var dashboardGroups = progress.repeatedErrors(owner.id());
    assertEquals(1, dashboardGroups.size());
    assertEquals(2, dashboardGroups.getFirst().number("sentences"));
    assertEquals(1, jdbc.queryForObject("""
        SELECT COUNT(*) FROM notifications WHERE user_id=? AND dedupe_key=?
        """, Integer.class, owner.id(), "feedback:" + id));
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
    assertEquals("SENTENCE", json.readTree(card.body()).get("partOfSpeech").asString());
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
    java.time.Clock reviewClock() {
      return java.time.Clock.fixed(java.time.Instant.parse("2026-11-27T02:00:00Z"), java.time.ZoneOffset.UTC);
    }

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
"exampleEn":"May I have some tea, please?","partOfSpeech":"SENTENCE"}],"nextPractice":"Thử gọi thêm đồ uống và xác nhận đơn."}
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
