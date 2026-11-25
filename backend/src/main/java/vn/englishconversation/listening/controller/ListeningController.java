package vn.englishconversation.listening.controller;

import static vn.englishconversation.listening.dto.ListeningData.*;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.listening.service.ListeningAttempts;
import vn.englishconversation.listening.service.ListeningPublication;

@RestController
@RequestMapping("/api/v1")
@Profile({"g4", "g5", "g6"})
public class ListeningController {
  private final ListeningAttempts attempts;
  private final ListeningPublication publication;

  public ListeningController(ListeningAttempts attempts, ListeningPublication publication) {
    this.attempts = attempts;
    this.publication = publication;
  }

  @GetMapping("/lessons/{id}/listening")
  public List<Exercise> available(@PathVariable long id) { return attempts.available(id); }

  @GetMapping("/listening/exercises")
  public ExercisePage available(@RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "12") int size) {
    return attempts.available(page, size);
  }

  @GetMapping("/listening/attempts")
  public HistoryPage history(@AuthenticationPrincipal AccountPrincipal owner,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
    return attempts.history(owner, page, size);
  }

  @PostMapping("/listening/exercises/{id}/attempts")
  public Attempt start(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id,
      @Valid @RequestBody Start input) {
    return attempts.start(owner, id, input);
  }

  @GetMapping("/listening/attempts/{id}")
  public ResponseEntity<Attempt> detail(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(attempts.get(owner, id));
  }

  @PutMapping("/listening/attempts/{id}/draft")
  public Attempt draft(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id,
      @Valid @RequestBody Answers input) {
    return attempts.save(owner, id, input);
  }

  @PostMapping("/listening/attempts/{id}/submit")
  public Attempt submit(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id,
      @Valid @RequestBody Answers input) {
    return attempts.submit(owner, id, input);
  }

  @PostMapping("/listening/attempts/{id}/abandon")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void abandon(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    attempts.abandon(owner, id);
  }

  @GetMapping("/listening/attempts/{id}/audio")
  public ResponseEntity<Resource> audio(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("audio/mpeg"))
        .body(publication.audio(attempts.audioExercise(owner, id)));
  }

  @GetMapping("/admin/listening-exercises")
  public List<DraftExercise> versions() { return publication.versions(); }

  @PostMapping("/admin/listening-exercises/{id}/publish")
  public DraftExercise publish(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id) {
    return publication.publish(actor, id);
  }
}
