package vn.englishconversation.auth.controller;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.dto.ChangePasswordRequest;
import vn.englishconversation.auth.dto.CredentialsRequest;
import vn.englishconversation.auth.dto.CsrfResponse;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountRateLimiter;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.user.dto.UserResponse;

@RestController
@RequestMapping("/api/v1/auth")
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class AuthController {
  private final AccountService accounts;
  private final AccountRateLimiter rateLimiter;
  private final SecurityContextRepository contexts;
  private final SessionAuthenticationStrategy strategy;

  public AuthController(
      AccountService accounts, AccountRateLimiter rateLimiter,
      SecurityContextRepository contexts,
      SessionAuthenticationStrategy strategy) {
    this.accounts = accounts;
    this.rateLimiter = rateLimiter;
    this.contexts = contexts;
    this.strategy = strategy;
  }

  @GetMapping("/csrf")
  public CsrfResponse csrf(CsrfToken token, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return new CsrfResponse(token.getToken(), token.getHeaderName());
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse register(@Valid @RequestBody CredentialsRequest c, HttpServletRequest request) {
    rateLimiter.check("register-ip:"+request.getRemoteAddr(),30,0);
    return accounts.register(c.email(), c.password());
  }

  @PostMapping("/login")
  public UserResponse login(
      @Valid @RequestBody CredentialsRequest c,
      HttpServletRequest request,
      HttpServletResponse response) {
    var authentication = accounts.login(c.email(), c.password());
    strategy.onAuthentication(authentication, request, response);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);
    return accounts.current(((AccountPrincipal) authentication.getPrincipal()).id());
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal AccountPrincipal p) {
    return accounts.current(p.id());
  }

  @PostMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void password(
      @AuthenticationPrincipal AccountPrincipal p,
      @Valid @RequestBody ChangePasswordRequest c,
      HttpServletRequest req,
      HttpServletResponse res) {
    accounts.changePassword(p, c.currentPassword(), c.newPassword());
    new SecurityContextLogoutHandler().logout(req, res, null);
  }
}
