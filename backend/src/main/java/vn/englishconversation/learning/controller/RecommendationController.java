package vn.englishconversation.learning.controller;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.learning.dto.LearningRecommendation;
import vn.englishconversation.learning.dto.DifficultyAdvice;
import vn.englishconversation.learning.dto.LearningRecommendation.Focus;
import vn.englishconversation.learning.service.RecommendationService;

@RestController
@RequestMapping("/api/v1/learning/recommendations")
@Profile("g6 | (!bootstrap & !g1 & !g2 & !g3 & !g4 & !g5)")
public class RecommendationController {
  private final RecommendationService recommendations;

  public RecommendationController(RecommendationService recommendations) {
    this.recommendations = recommendations;
  }

  @GetMapping("/difficulty")
  public DifficultyAdvice difficulty(@AuthenticationPrincipal AccountPrincipal owner) {
    return recommendations.difficulty(owner.id());
  }

  @GetMapping
  public List<LearningRecommendation> list(@AuthenticationPrincipal AccountPrincipal owner,
      @RequestParam(required = false) Focus focus) {
    return recommendations.suggest(owner.id(), focus);
  }
}
