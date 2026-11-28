package vn.englishconversation.learning;


import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.englishconversation.conversation.dto.*;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class ProgressIntegrationMysqlTest extends FullSystemMysqlFixture {

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

  private int progressPostStatus(java.net.http.HttpClient browser, tools.jackson.databind.ObjectMapper json,
      String base, String path) {
    try { return post(browser,json,base,path,null,true).statusCode(); }
    catch (Exception failure) { throw new CompletionException(failure); }
  }
}
