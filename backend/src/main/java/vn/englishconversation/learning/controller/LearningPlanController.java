package vn.englishconversation.learning.controller;

import static vn.englishconversation.learning.dto.LearningPlanData.*;

import jakarta.validation.Valid;
import java.util.List;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.learning.service.LearningPlanService;

@RestController
@RequestMapping("/api/v1/learning/plans")
@FeatureEnabled(FeatureEnabled.Feature.PLANS)
public class LearningPlanController {
  private final LearningPlanService plans;

  public LearningPlanController(LearningPlanService plans) { this.plans = plans; }

  @GetMapping
  public List<Summary> list(@AuthenticationPrincipal AccountPrincipal owner,
      @RequestParam(defaultValue="0") int page) {
    return plans.list(owner, page);
  }

  @GetMapping("/{id}")
  public Detail detail(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return plans.detail(owner, id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Detail create(@AuthenticationPrincipal AccountPrincipal owner, @Valid @RequestBody Create input) {
    return plans.create(owner, input);
  }

  @PutMapping("/{id}")
  public Detail edit(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id, @Valid @RequestBody Edit input) {
    return plans.edit(owner, id, input);
  }

  @PostMapping("/{id}/items")
  @ResponseStatus(HttpStatus.CREATED)
  public Detail add(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id, @Valid @RequestBody AddItem input) {
    return plans.addItem(owner, id, input);
  }

  @PutMapping("/{id}/items/{item}")
  public Detail editItem(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id,
      @PathVariable long item, @Valid @RequestBody EditItem input) {
    return plans.editItem(owner, id, item, input);
  }

  @PostMapping("/{id}/items/{item}/finish")
  public Detail finish(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id,
      @PathVariable long item, @Valid @RequestBody FinishItem input) {
    return plans.finishItem(owner, id, item, input);
  }
}
