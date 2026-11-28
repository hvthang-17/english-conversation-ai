package vn.englishconversation.profile.controller;

import java.util.Map;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.profile.service.AvatarService;

@RestController
@FeatureEnabled(FeatureEnabled.Feature.CORE)
@RequestMapping(AvatarService.API_PATH)
public class AvatarController {
  private final AvatarService storage;

  public AvatarController(AvatarService storage) {
    this.storage = storage;
  }

  @GetMapping
  public ResponseEntity<byte[]> get(@AuthenticationPrincipal AccountPrincipal owner) {
    return image(storage.read(owner.id()));
  }

  @GetMapping("/{key}")
  public ResponseEntity<byte[]> getVersion(
      @AuthenticationPrincipal AccountPrincipal owner, @PathVariable String key) {
    return image(storage.read(owner.id(), key));
  }

  private ResponseEntity<byte[]> image(byte[] bytes) {
    return ResponseEntity.ok()
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.IMAGE_PNG)
        .body(bytes);
  }

  @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, String> upload(
      @AuthenticationPrincipal AccountPrincipal owner, @RequestPart("file") MultipartFile file) {
    return Map.of("avatarUrl", storage.save(owner, file));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AccountPrincipal owner) {
    storage.delete(owner);
  }
}
