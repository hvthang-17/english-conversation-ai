package vn.englishconversation.profile.controller;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.profile.dto.*;
import vn.englishconversation.profile.service.ProfileService;

@RestController
@RequestMapping("/api/v1/profile/me")
@Profile("!bootstrap")
public class ProfileController {
  private final ProfileService profiles;

  public ProfileController(ProfileService profiles) {
    this.profiles = profiles;
  }

  @GetMapping
  public ProfileResponse me(@AuthenticationPrincipal AccountPrincipal p) {
    return profiles.get(p.id());
  }

  @PutMapping
  public ProfileResponse update(
      @AuthenticationPrincipal AccountPrincipal p,
      @Valid @RequestBody UpdateProfileRequest request) {
    return profiles.update(p, request);
  }
}
