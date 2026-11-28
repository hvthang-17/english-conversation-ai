package vn.englishconversation.conversation.controller;

import jakarta.validation.Valid;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.conversation.dto.*;
import vn.englishconversation.conversation.service.PracticeService;
import vn.englishconversation.voice.dto.ConfirmVoiceRequest;
import vn.englishconversation.voice.dto.VoiceDraft;
import vn.englishconversation.voice.service.WaveAudio;

@RestController
@RequestMapping("/api/v1/practice-sessions")
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class PracticeController {
  private final PracticeService practice;

  private final vn.englishconversation.voice.service.VoiceDraftService voice;
  private final vn.englishconversation.conversation.service.HintService hints;
  private final vn.englishconversation.conversation.service.ExplanationService explanations;

  public PracticeController(PracticeService practice,
      vn.englishconversation.voice.service.VoiceDraftService voice,
      vn.englishconversation.conversation.service.HintService hints,
      vn.englishconversation.conversation.service.ExplanationService explanations) {
    this.voice = voice; this.hints = hints; this.explanations = explanations;
    this.practice = practice;
  }

  @PostMapping
  public PracticeResponse create(
      @AuthenticationPrincipal AccountPrincipal owner,
      @Valid @RequestBody CreatePracticeRequest request) {
    return practice.create(owner, request);
  }

  @GetMapping("/{id}")
  public PracticeResponse get(
      @AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return practice.get(owner, id);
  }

  @PatchMapping("/{id}/mode")
  public PracticeResponse updateMode(
      @AuthenticationPrincipal AccountPrincipal owner,
      @PathVariable long id,
      @Valid @RequestBody ChangePracticeModeRequest request) {
    return practice.switchToText(owner, id);
  }

  @PostMapping("/{id}/turns")
  public PracticeResponse send(
      @AuthenticationPrincipal AccountPrincipal owner,
      @PathVariable long id,
      @Valid @RequestBody SendTextRequest request) {
    return practice.send(owner, id, request);
  }

  @PostMapping(path = "/{id}/audio", consumes = "multipart/form-data")
  public VoiceDraft uploadVoice(
      @AuthenticationPrincipal AccountPrincipal owner,
      @PathVariable long id,
      @RequestParam String clientRequestId,
      @RequestParam(defaultValue = "false") boolean retry,
      @RequestPart MultipartFile file) {
    var audio = WaveAudio.read(file, WaveAudio.CONVERSATION_MAX_SECONDS);
    return voice.uploadVoice(owner, id, clientRequestId, audio, retry);
  }

  @GetMapping("/{id}/audio")
  public ResponseEntity<VoiceDraft> recoverVoice(
      @AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return voice.recoverVoice(owner, id).map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @GetMapping("/{id}/audio/{audioId}")
  public VoiceDraft getVoice(
      @AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id, @PathVariable long audioId) {
    return voice.getVoice(owner, id, audioId);
  }

  @PostMapping("/{id}/audio/{audioId}/confirm")
  public PracticeResponse confirmVoice(
      @AuthenticationPrincipal AccountPrincipal owner,
      @PathVariable long id,
      @PathVariable long audioId,
      @Valid @RequestBody ConfirmVoiceRequest request) {
    return voice.confirmVoice(owner, id, audioId, request.content(), request.assessPronunciation());
  }

  @DeleteMapping("/{id}/audio/{audioId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void discardVoice(
      @AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id, @PathVariable long audioId) {
    voice.discardVoice(owner, id, audioId);
  }

  @PostMapping("/{id}/hints")
  public HintResponse hint(
      @AuthenticationPrincipal AccountPrincipal owner,
      @PathVariable long id,
      @Valid @RequestBody HintRequest request) {
    return hints.hint(owner, id, request);
  }

  @GetMapping("/{id}/messages/{messageId}/hint")
  public HintResponse getHint(@AuthenticationPrincipal AccountPrincipal owner,
      @PathVariable long id, @PathVariable long messageId) {
    return hints.getHint(owner, id, messageId);
  }

  @GetMapping("/{id}/messages/{messageId}/explanation")
  public ExplanationResponse explanation(@AuthenticationPrincipal AccountPrincipal owner,
      @PathVariable long id, @PathVariable long messageId) {
    return explanations.getExplanation(owner, id, messageId);
  }

  @PostMapping("/{id}/messages/{messageId}/explanation")
  public ExplanationResponse explain(@AuthenticationPrincipal AccountPrincipal owner,
      @PathVariable long id, @PathVariable long messageId, @Valid @RequestBody ExplanationRequest input) {
    return explanations.explain(owner, id, messageId, input);
  }

  @PostMapping("/{id}/operations/{operation}/retry")
  public PracticeResponse retry(
      @AuthenticationPrincipal AccountPrincipal owner,
      @PathVariable long id,
      @PathVariable long operation,
      @RequestBody RetryRequest request) {
    return practice.retry(owner, id, operation, request);
  }

  @PostMapping("/{id}/operations/{operation}/skip")
  public PracticeResponse skip(
      @AuthenticationPrincipal AccountPrincipal owner,
      @PathVariable long id,
      @PathVariable long operation) {
    return practice.skip(owner, id, operation);
  }

  @PostMapping("/{id}/finish")
  public PracticeResponse finish(
      @AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return practice.finish(owner, id);
  }
}
