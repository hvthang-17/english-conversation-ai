package vn.englishconversation.auth.controller;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountLifecycleService;
import vn.englishconversation.auth.service.AccountRateLimiter;

@RestController
@RequestMapping("/api/v1/auth")
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class AccountLifecycleController {
  private final AccountLifecycleService lifecycle;
  private final AccountRateLimiter rateLimiter;

  public AccountLifecycleController(
      AccountLifecycleService lifecycle, AccountRateLimiter rateLimiter) {
    this.lifecycle = lifecycle;
    this.rateLimiter = rateLimiter;
  }

  public record Email(@NotBlank @Size(max = 254) String email) {}

  public record Reset(
      @NotBlank @Size(max = 100) String token,
      @NotNull @Size(min = 12, max = 128) String newPassword) {}

  public record Delete(
      @NotNull @Size(max = 128) String currentPassword, @NotNull String confirmation) {}

  @PostMapping("/password-reset/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void resetRequest(@Valid @RequestBody Email input, HttpServletRequest request) {
    rateLimiter.check("reset-ip:" + request.getRemoteAddr(), 30, 0);
    lifecycle.requestReset(input.email());
  }

  @PostMapping("/password-reset/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reset(@Valid @RequestBody Reset input, HttpServletRequest request) {
    rateLimiter.check("token-ip:" + request.getRemoteAddr(), 30, 0);
    lifecycle.confirmReset(input.token(), input.newPassword());
  }

  @DeleteMapping("/account")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void delete(
      @AuthenticationPrincipal AccountPrincipal owner,
      @Valid @RequestBody Delete input,
      HttpServletRequest req,
      HttpServletResponse res) {
    lifecycle.delete(owner, input.currentPassword(), input.confirmation());
    new SecurityContextLogoutHandler().logout(req, res, null);
    var cookie = new Cookie("SESSION", "");
    cookie.setMaxAge(0);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setSecure(req.isSecure());
    res.addCookie(cookie);
  }
}
