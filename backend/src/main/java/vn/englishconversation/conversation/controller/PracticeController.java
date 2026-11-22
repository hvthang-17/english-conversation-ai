package vn.englishconversation.conversation.controller;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.conversation.dto.*;
import vn.englishconversation.conversation.service.PracticeService;
import vn.englishconversation.voice.*;

@RestController
@RequestMapping("/api/v1/practice-sessions")
@Profile("!bootstrap")
public class PracticeController {
  private final PracticeService practice;

  public PracticeController(PracticeService practice) {
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
    return practice.uploadVoice(owner, id, clientRequestId, WaveAudio.read(file), retry);
  }

  @GetMapping("/{id}/audio/{audioId}")
  public VoiceDraft getVoice(
      @AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id, @PathVariable long audioId) {
    return practice.getVoice(owner, id, audioId);
  }

  @PostMapping("/{id}/audio/{audioId}/confirm")
  public PracticeResponse confirmVoice(
      @AuthenticationPrincipal AccountPrincipal owner,
      @PathVariable long id,
      @PathVariable long audioId,
      @Valid @RequestBody ConfirmVoiceRequest request) {
    return practice.confirmVoice(owner, id, audioId, request.content());
  }

  @DeleteMapping("/{id}/audio/{audioId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void discardVoice(
      @AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id, @PathVariable long audioId) {
    practice.discardVoice(owner, id, audioId);
  }

  @PostMapping("/{id}/hints")
  public PracticeResponse hint(
      @AuthenticationPrincipal AccountPrincipal owner,
      @PathVariable long id,
      @Valid @RequestBody HintRequest request) {
    return practice.hint(owner, id, request);
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
