package vn.englishconversation.pronunciation.controller;

import static vn.englishconversation.pronunciation.dto.PronunciationData.*;

import java.util.List;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.pronunciation.service.PronunciationService;

@RestController
@FeatureEnabled(FeatureEnabled.Feature.PRONUNCIATION)
@RequestMapping("/api/v1/pronunciation")
public class PronunciationController {
  private final PronunciationService service;

  public PronunciationController(PronunciationService service) {
    this.service = service;
  }

  public record Catalog(List<Drill> drills, boolean scoringAvailable) {}

  @GetMapping("/drills")
  public ResponseEntity<Catalog> drills() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new Catalog(service.drills(), service.scoringAvailable()));
  }

  @PostMapping(value = "/drills/{id}/attempts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Attempt> submit(@AuthenticationPrincipal AccountPrincipal user, @PathVariable long id,
      @RequestParam String requestId, @RequestParam MultipartFile audio) {
    return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
        .body(service.submit(user.id(), id, requestId, audio));
  }

  @GetMapping("/attempts/{id}")
  public ResponseEntity<Attempt> attempt(@AuthenticationPrincipal AccountPrincipal user, @PathVariable long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(user.id(), id));
  }

  @GetMapping("/attempts")
  public ResponseEntity<History> history(@AuthenticationPrincipal AccountPrincipal user,
      @RequestParam(defaultValue = "0") int page) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(user.id(), page));
  }
}
