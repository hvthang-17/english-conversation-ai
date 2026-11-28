package vn.englishconversation.learning.controller;

import static vn.englishconversation.learning.dto.LearningData.*;

import jakarta.validation.Valid;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.learning.service.LearningService;

@RestController
@RequestMapping("/api/v1/learning")
@FeatureEnabled(FeatureEnabled.Feature.LEARNING)
public class LearningController {
  private final LearningService learning;

  public LearningController(LearningService learning) {
    this.learning = learning;
  }

  @GetMapping("/cards")
  public Page<Card> cards(
      @AuthenticationPrincipal AccountPrincipal owner,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "12") int size) {
    return learning.cards(owner, status, page, size);
  }

  @PostMapping("/cards/from-suggestion/{suggestion}")
  public Card save(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long suggestion) {
    return learning.save(owner, suggestion);
  }

  @PostMapping("/cards/{id}/attempts")
  public Card answer(
      @AuthenticationPrincipal AccountPrincipal owner,
      @PathVariable long id,
      @Valid @RequestBody ReviewAnswer answer) {
    return learning.answer(owner, id, answer);
  }

  @GetMapping("/history")
  public Page<HistoryEntry> history(
      @AuthenticationPrincipal AccountPrincipal owner,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "12") int size) {
    return learning.history(owner, page, size);
  }

  @DeleteMapping("/history/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteSession(
      @AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    learning.deleteSession(owner, id);
  }

  @DeleteMapping("/data")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteAll(@AuthenticationPrincipal AccountPrincipal owner) {
    learning.deleteAll(owner);
  }

  @GetMapping("/stats")
  public Statistics statistics(@AuthenticationPrincipal AccountPrincipal owner) {
    return learning.statistics(owner);
  }
}
