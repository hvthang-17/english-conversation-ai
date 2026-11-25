package vn.englishconversation.profile.controller;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.profile.service.AvatarService;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/v1/profile/me/avatar")
public class AvatarController {
  private final AvatarService storage;

  public AvatarController(AvatarService storage) {
    this.storage = storage;
  }

  @GetMapping
  public ResponseEntity<byte[]> get(@AuthenticationPrincipal AccountPrincipal owner) {
    return ResponseEntity.ok()
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.IMAGE_PNG)
        .body(storage.read(owner.id()));
  }

  @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, String> upload(
      @AuthenticationPrincipal AccountPrincipal owner, @RequestPart("file") MultipartFile file) {
    storage.save(owner, file);
    return Map.of("avatarUrl", "/api/v1/profile/me/avatar");
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AccountPrincipal owner) {
    storage.delete(owner);
  }
}
