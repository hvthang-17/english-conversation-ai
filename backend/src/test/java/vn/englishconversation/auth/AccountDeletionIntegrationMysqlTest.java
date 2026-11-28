package vn.englishconversation.auth;


import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.conversation.dto.*;
import vn.englishconversation.voice.service.WaveAudio;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class AccountDeletionIntegrationMysqlTest extends FullSystemMysqlFixture {

  @Test
  void accountDeletionRejectsLateAiAndRemovesPracticeAudioAndRequests() throws Exception {
    var owner=owner();long id=Long.parseLong(service.create(owner,voicePractice()).id());
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
}
