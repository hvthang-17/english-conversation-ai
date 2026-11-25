package vn.englishconversation.system.controller;

import java.time.Instant;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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

  public HealthController(Environment environment) {
    learningWorkspaceAvailable = environment.acceptsProfiles(Profiles.of("!bootstrap & !g1 & !g2 & !g3 & !g4 & !g5"));
    lessonCatalogAvailable = environment.acceptsProfiles(Profiles.of("!bootstrap & !g1"));
    lessonMaterialsAvailable = environment.acceptsProfiles(Profiles.of("!bootstrap & !g1 & !g2"));
    listeningAvailable = environment.acceptsProfiles(Profiles.of("g4", "g5", "g6"));
    pronunciationAvailable = environment.acceptsProfiles(Profiles.of("g5", "g6"));
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
