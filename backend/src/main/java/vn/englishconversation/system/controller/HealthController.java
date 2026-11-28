package vn.englishconversation.system.controller;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.englishconversation.system.dto.HealthResponse;

@RestController
@RequestMapping("/api/v1/system")
public class HealthController {
  private final boolean learningWorkspaceAvailable;
  private final boolean lessonCatalogAvailable;
  private final boolean lessonMaterialsAvailable;
  private final boolean listeningAvailable;
  private final boolean pronunciationAvailable;

  public HealthController(vn.englishconversation.config.features.FeatureSet features) {
    learningWorkspaceAvailable = features.enabled(vn.englishconversation.config.features.FeatureEnabled.Feature.LEARNING);
    lessonCatalogAvailable = features.enabled(vn.englishconversation.config.features.FeatureEnabled.Feature.CATALOG);
    lessonMaterialsAvailable = features.enabled(vn.englishconversation.config.features.FeatureEnabled.Feature.MATERIALS);
    listeningAvailable = features.enabled(vn.englishconversation.config.features.FeatureEnabled.Feature.LISTENING);
    pronunciationAvailable = features.enabled(vn.englishconversation.config.features.FeatureEnabled.Feature.PRONUNCIATION);
  }

  // Process liveness only; this endpoint does not check MySQL or Gemini.
  @GetMapping("/health")
  public HealthResponse health() {
    return new HealthResponse("UP", "english-conversation-ai", Instant.now());
  }

  @GetMapping("/features")
  public Features features() {
    return new Features(learningWorkspaceAvailable, lessonCatalogAvailable, lessonMaterialsAvailable, listeningAvailable, pronunciationAvailable);
  }

  public record Features(boolean learningWorkspaceAvailable, boolean lessonCatalogAvailable, boolean lessonMaterialsAvailable,
                         boolean listeningAvailable, boolean pronunciationAvailable) {}
}
