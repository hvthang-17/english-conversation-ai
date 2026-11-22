package vn.englishconversation.feedback.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.conversation.dto.RetryRequest;
import vn.englishconversation.conversation.service.PracticeService;
import vn.englishconversation.feedback.dto.FeedbackResponse;

@RestController
@RequestMapping("/api/v1/practice-sessions/{id}/feedback")
@Profile("!bootstrap")
public class FeedbackController {
  private final PracticeService practice;

  public FeedbackController(PracticeService practice) {
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
