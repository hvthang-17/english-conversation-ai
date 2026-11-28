package vn.englishconversation.voice;


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
class VoicePracticeMysqlTest extends FullSystemMysqlFixture {

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
    assertNotNull(confirmed.messages().get(1).pronunciation());
    assertEquals("COMPLETED", service.finish(owner, session).status());
    for (int n = 0; n < 45; n++) {
      if ("UNKNOWN".equals(service.get(owner, session).messages().get(1).pronunciation().status())) {
        assertEquals(1, jdbc.queryForObject(count, Integer.class, audio));
        return;
      }
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
    assertEquals("AUDIO_EXPIRED", confirmed.messages().get(1).pronunciation().errorCode());
    assertEquals("AUDIO_EXPIRED", service.get(owner, session).messages().get(1).pronunciation().errorCode());
    org.mockito.Mockito.verify(pronunciationProvider, org.mockito.Mockito.never())
        .assess(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "G6_AZURE_FIXTURE", matches = "true")
  void voiceAssessmentKeepsPreparationReasonsAfterAudioCleanup() throws Exception {
    org.mockito.Mockito.when(pronunciationProvider.available()).thenReturn(true);
    var owner = owner();
    long session = Long.parseLong(service.create(owner, voicePractice()).id());
    var wave = WaveAudio.read(wave(16000, 123, 1), WaveAudio.CONVERSATION_MAX_SECONDS);
    long missing = Long.parseLong(service.uploadVoice(owner, session, "missing-audio-file", wave, false).id());
    String key = jdbc.queryForObject("SELECT storage_key FROM audio_submissions WHERE id=?", String.class, missing);
    java.nio.file.Files.delete(voiceStorage.path(key));
    assertEquals(1, service.confirmVoice(owner, session, missing, "First fixture.", true).learnerTurns());
    conversationPronunciation.preparePending();
    assertEquals("AUDIO_MISSING", service.get(owner, session).messages().get(1).pronunciation().errorCode());

    long failed = Long.parseLong(service.uploadVoice(owner, session, "occupied-job-key", wave, false).id());
    jdbc.update("""
        INSERT INTO ai_requests(user_id,session_id,task_type,operation_key,request_fingerprint,prompt_version,created_at)
        VALUES (?,?,'PRONUNCIATION',?,REPEAT('a',64),'fixture',UTC_TIMESTAMP(6))
        """, owner.id(), session, "voice:" + failed);
    assertEquals(2, service.confirmVoice(owner, session, failed, "Second fixture.", true).learnerTurns());
    conversationPronunciation.preparePending();
    voiceStorage.sweep();
    assertEquals("PREPARATION_FAILED", service.get(owner, session).messages().get(3).pronunciation().errorCode());
    assertNull(jdbc.queryForObject("SELECT storage_key FROM audio_submissions WHERE id=?", String.class, failed));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pronunciation_attempts WHERE audio_submission_id IN (?,?)",
        Integer.class, missing, failed));

    long legacy = Long.parseLong(service.uploadVoice(owner, session, "legacy-audio-draft", wave, false).id());
    jdbc.update("UPDATE audio_submissions SET duration_ms=30001 WHERE id=?", legacy);
    var confirmed = service.confirmVoice(owner, session, legacy, "Legacy fixture.", false);
    assertEquals(3, confirmed.learnerTurns());
    assertEquals("TOO_LONG", confirmed.messages().get(5).pronunciation().errorCode());
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "G6_AZURE_FIXTURE", matches = "true")
  void voiceAssessmentPreservesQuotaAndInsufficientSpeechReasons() throws Exception {
    org.mockito.Mockito.when(pronunciationProvider.available()).thenReturn(true);
    org.mockito.Mockito.when(pronunciationProvider.assess(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.eq("en-US")))
        .thenThrow(new vn.englishconversation.pronunciation.service.PronunciationProvider.Failure(
            "AZURE_QUOTA", false, false))
        .thenReturn(null);
    var owner = owner();
    long session = Long.parseLong(service.create(owner, voicePractice()).id());
    var wave = WaveAudio.read(wave(16000, 123, 1), WaveAudio.CONVERSATION_MAX_SECONDS);
    var reasons = List.of("AZURE_QUOTA", "NO_SPEECH_EVIDENCE");
    for (int n = 0; n < reasons.size(); n++) {
      long audio = Long.parseLong(service.uploadVoice(owner, session, "assessment-reason-" + n, wave, false).id());
      assertEquals(n + 1, service.confirmVoice(owner, session, audio, "Fixture speech.", true).learnerTurns());
      int messageIndex = n * 2 + 1;
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
      var assessment = service.get(owner, session).messages().get(messageIndex).pronunciation();
      while (assessment.errorCode() == null && System.nanoTime() < end) {
        Thread.sleep(150);
        assessment = service.get(owner, session).messages().get(messageIndex).pronunciation();
      }
      assertEquals(reasons.get(n), assessment.errorCode());
      assertNull(assessment.overall());
    }
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

  @Test
  void validatesActualPcmAndDuration() {
    assertEquals(400, assertThrows(ApiException.class, () -> WaveAudio.read(wave(1, 0, 1))).status);
    assertEquals(400, assertThrows(ApiException.class, () -> WaveAudio.read(wave(1, 10, 3))).status);
    assertEquals(413, assertThrows(ApiException.class, () -> WaveAudio.read(wave(960001, 10, 1))).status);
    assertEquals(60000, WaveAudio.read(wave(960000, 10, 1)).durationMillis());
    int limit = WaveAudio.CONVERSATION_MAX_SECONDS;
    assertEquals(30000, WaveAudio.read(wave(480000, 10, 1), limit).durationMillis());
    assertEquals(413, assertThrows(ApiException.class, () -> WaveAudio.read(wave(480001, 10, 1), limit)).status);
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
}
