package vn.englishconversation.support;


import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.englishconversation.conversation.dto.*;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class SupportIntegrationMysqlTest extends FullSystemMysqlFixture {

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
}
