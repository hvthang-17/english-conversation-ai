package vn.englishconversation.system.controller;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.englishconversation.system.dto.HealthResponse;

@RestController
@RequestMapping("/api/v1/system")
public class HealthController {

  // Process liveness only; this endpoint does not check MySQL or Gemini.
  @GetMapping("/health")
  public HealthResponse health() {
    return new HealthResponse("UP", "english-conversation-ai", Instant.now());
  }
}
