package vn.englishconversation.feedback.controller;

import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.conversation.dto.RetryRequest;
import vn.englishconversation.feedback.service.FeedbackService;
import vn.englishconversation.feedback.dto.FeedbackResponse;

@RestController
@RequestMapping("/api/v1/practice-sessions/{id}/feedback")
@FeatureEnabled(FeatureEnabled.Feature.FEEDBACK)
public class FeedbackController {
  private final FeedbackService practice;

  public FeedbackController(FeedbackService practice) {
    this.practice = practice;
  }

  @GetMapping
  public FeedbackResponse get(
      @AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return practice.getFeedback(owner, id);
  }

  @PostMapping
  public FeedbackResponse generate(
      @AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return practice.generateFeedback(owner, id, false, false);
  }

  @PostMapping("/retry")
  public FeedbackResponse retry(
      @AuthenticationPrincipal AccountPrincipal owner,
      @PathVariable long id,
      @RequestBody RetryRequest request) {
    return practice.generateFeedback(owner, id, true, request.acknowledgeUnknown());
  }
}
