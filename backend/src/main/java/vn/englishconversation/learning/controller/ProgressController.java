package vn.englishconversation.learning.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.learning.service.ProgressService;
import vn.englishconversation.learning.service.ProgressService.*;

@RestController
@RequestMapping("/api/v1/learning/progress")
@Profile("g6")
public class ProgressController {
  private final ProgressService progress;
  public ProgressController(ProgressService progress) { this.progress = progress; }
  public record Pulse(@NotNull UUID eventId, @NotNull UUID streamId, @NotNull Activity activity) {}

  @GetMapping
  public Dashboard dashboard(@AuthenticationPrincipal AccountPrincipal owner,
      @RequestParam(required=false) @Pattern(regexp="A2|B1") String level) {
    return progress.dashboard(owner, level);
  }

  @GetMapping("/lessons/{id}")
  public Completion completion(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return progress.completion(owner, id);
  }

  @PostMapping("/lessons/{id}")
  public Completion mark(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return progress.mark(owner, id);
  }

  @PostMapping("/heartbeat")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void pulse(@AuthenticationPrincipal AccountPrincipal owner, @Valid @RequestBody Pulse pulse) {
    progress.heartbeat(owner, pulse.eventId(), pulse.streamId(), pulse.activity());
  }

  @GetMapping("/notifications")
  public List<Notification> notifications(@AuthenticationPrincipal AccountPrincipal owner,
      @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page) {
    return progress.notifications(owner, page);
  }

  @PutMapping("/notifications/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void read(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    progress.read(owner, id);
  }
}
