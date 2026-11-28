package vn.englishconversation.conversation.service;

import java.util.*;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import vn.englishconversation.ai.provider.TextProvider.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.conversation.dto.*;
import vn.englishconversation.feedback.dto.*;
import vn.englishconversation.voice.dto.VoiceDraft;
import vn.englishconversation.voice.service.WaveAudio;

import vn.englishconversation.voice.service.VoiceDraftService;
import vn.englishconversation.feedback.service.FeedbackService;

@Service
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class PracticeService {
  private final PracticeSessionService sessions;
  private final VoiceDraftService voice;
  private final FeedbackService feedbackService;
  private final HintService hints;
  private final ExplanationService explanations;
  public PracticeService(PracticeSessionService sessions, VoiceDraftService voice, FeedbackService feedbackService, HintService hints, ExplanationService explanations) {
    this.sessions = sessions;
    this.voice = voice;
    this.feedbackService = feedbackService;
    this.hints = hints;
    this.explanations = explanations;
  }
  public PracticeResponse create(AccountPrincipal owner, CreatePracticeRequest input) { return sessions.create(owner, input); }
  public PracticeResponse switchToText(AccountPrincipal owner, long id) { return sessions.switchToText(owner, id); }
  public PracticeResponse send(AccountPrincipal owner, long id, SendTextRequest input) { return sessions.send(owner, id, input); }
  public VoiceDraft uploadVoice(
      AccountPrincipal owner, long id, String clientId, WaveAudio audio, boolean retry) { return voice.uploadVoice(owner, id, clientId, audio, retry); }
  public VoiceDraft getVoice(AccountPrincipal owner, long id, long audioId) { return voice.getVoice(owner, id, audioId); }
  public Optional<VoiceDraft> recoverVoice(AccountPrincipal owner, long id) { return voice.recoverVoice(owner, id); }
  public PracticeResponse confirmVoice(AccountPrincipal owner, long id, long audioId, String content) { return voice.confirmVoice(owner, id, audioId, content); }
  public PracticeResponse confirmVoice(AccountPrincipal owner, long id, long audioId, String content,
      boolean assessPronunciation) { return voice.confirmVoice(owner, id, audioId, content, assessPronunciation); }
  public void discardVoice(AccountPrincipal owner, long id, long audioId) { voice.discardVoice(owner, id, audioId); }
  public HintResponse hint(AccountPrincipal owner, long id, HintRequest input) { return hints.hint(owner, id, input); }
  public HintResponse getHint(AccountPrincipal owner, long id, long message) { return hints.getHint(owner, id, message); }
  public PracticeResponse retry(
      AccountPrincipal owner, long id, long operation, RetryRequest input) { return sessions.retry(owner, id, operation, input); }
  public PracticeResponse skip(AccountPrincipal owner, long id, long operation) { return sessions.skip(owner, id, operation); }
  public PracticeResponse finish(AccountPrincipal owner, long id) { return sessions.finish(owner, id); }
  public PracticeResponse get(AccountPrincipal owner, long id) { return sessions.get(owner, id); }
  public FeedbackResponse getFeedback(AccountPrincipal owner, long id) { return feedbackService.getFeedback(owner, id); }
  public FeedbackResponse generateFeedback(
      AccountPrincipal owner, long id, boolean retry, boolean acknowledgeUnknown) { return feedbackService.generateFeedback(owner, id, retry, acknowledgeUnknown); }
  public ExplanationResponse getExplanation(AccountPrincipal owner, long id, long message) { return explanations.getExplanation(owner, id, message); }
  public ExplanationResponse explain(AccountPrincipal owner, long id, long message, ExplanationRequest input) { return explanations.explain(owner, id, message, input); }
}
