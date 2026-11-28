package vn.englishconversation.auth.service;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.security.TokenCipher;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.auth.repository.AccountLifecycleRepository;
import vn.englishconversation.mail.provider.MailGateway;
import vn.englishconversation.user.repository.UserRepository;

@Service
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class AccountLifecycleService {
  private final AccountLifecycleRepository persistence;
  private final AccountRateLimiter rateLimiter;
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final JdbcIndexedSessionRepository sessions;
  private final TokenCipher cipher;
  private final MailGateway mail;
  private final TransactionTemplate tx;
  private final String publicUrl;

  public AccountLifecycleService(AccountLifecycleRepository persistence,
      AccountRateLimiter rateLimiter,
      UserRepository users,
      PasswordEncoder passwords,
      JdbcIndexedSessionRepository sessions,
      TokenCipher cipher,
      MailGateway mail,
      PlatformTransactionManager manager,
      @Value("${app.mail.public-url:http://localhost:5173}") String publicUrl) {
    this.persistence = persistence;
    this.rateLimiter = rateLimiter;
    this.users = users;
    this.passwords = passwords;
    this.sessions = sessions;
    this.cipher = cipher;
    this.mail = mail;
    this.tx = new TransactionTemplate(manager);
    var uri = java.net.URI.create(publicUrl);
    if (!Set.of("http", "https").contains(uri.getScheme())
        || uri.getHost() == null
        || uri.getRawFragment() != null
        || uri.getRawQuery() != null
        || uri.getUserInfo() != null)
      throw new IllegalArgumentException("Invalid public frontend URL");
    this.publicUrl = publicUrl.replaceAll("/$", "");
    if (mail.available() && !cipher.available())
      throw new IllegalArgumentException("Enabled SMTP requires outbox encryption key");
  }

  public String publicUrl() {
    return publicUrl;
  }

  public boolean mailAvailable() {
    return mail.available() && cipher.available();
  }

  public void invalidateReset(long id) {
    persistence.consumeActiveTokens(id);
    persistence.cancelPendingMail(id);
  }

  public void requestReset(String raw) {
    String email = AccountService.email(raw);
    // Same throttling path for existing and nonexistent addresses.
    rateLimiter.check("reset:" + email, 5, 60);
    tx.executeWithoutResult(
        s -> {
          var user = users.lockByEmail(email);
          if (user.isPresent()
              && "ACTIVE".equals(user.get().status)
              && user.get().deletionRequestedAt == null
              && mailAvailable()) issue(user.get().id, user.get().email);
        });
  }

  private void issue(long id, String email) {
    invalidateReset(id);
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    Instant expires = Instant.now().plusSeconds(1800);
    long tokenId =
        persistence.createToken(id, email, TokenCipher.hash(token), Timestamp.from(expires));
    persistence.queueResetMail(id, tokenId, cipher.encrypt(token), Timestamp.from(expires));
  }

  public void confirmReset(String token, String newPassword) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw tokenError();
    AccountService.password(newPassword);
    tx.executeWithoutResult(
        s -> {
          // Lock owner before token, consistent with issue/reset/delete lock ordering.
          var initial =
              persistence.tokenOwner(TokenCipher.hash(token))
                  .orElseThrow(AccountLifecycleService::tokenError);
          var user =
              users
                  .lockById(initial.userId())
                  .orElseThrow(AccountLifecycleService::tokenError);
          var t =
              persistence.lockValidToken(TokenCipher.hash(token))
                  .orElseThrow(AccountLifecycleService::tokenError);
          if (!"ACTIVE".equals(user.status)
              || user.deletionRequestedAt != null
              || !user.email.equals(t.email())) throw tokenError();
          user.passwordHash = passwords.encode(newPassword);
          user.passwordChangedAt = Instant.now();
          user.securityVersion++;
          user.updatedAt = Instant.now();
          users.flush();
          invalidateReset(user.id);
          revoke(user.id);
        });
  }

  public void delete(AccountPrincipal owner, String password, String confirmation) {
    tx.executeWithoutResult(
        s -> {
          var user = users.lockById(owner.id()).orElseThrow(AccountLifecycleService::invalid);
          if (!AccountService.valid(user, owner)) throw invalid();
          if ("ADMIN".equals(user.role))
            throw new ApiException(409, "ADMIN_SELF_DELETE", "Hãy nhờ quản trị viên khác chuyển tài khoản của bạn sang người học trước khi xóa.");
          if (!"DELETE".equals(confirmation) || !passwords.matches(password, user.passwordHash))
            throw new ApiException(
                400,
                "DELETE_CONFIRMATION_INVALID",
                "Kiểm tra mật khẩu và nhập DELETE để xác nhận.");
          user.deletionRequestedAt = user.updatedAt = Instant.now();
          user.securityVersion++;
          users.flush();
          revoke(user.id);
          persistence.consumeAllTokens(user.id);
          persistence.cancelAllMail(user.id);
          persistence.disableSchedule(user.id);
          persistence.cancelReminders(user.id);
          persistence.requestDeletion(user.id);
        });
  }

  private void revoke(long id) {
    sessions.findByPrincipalName(Long.toString(id)).keySet().forEach(sessions::deleteById);
  }

  private static ApiException invalid() {
    return new ApiException(401, "SESSION_REVOKED", "Vui lòng đăng nhập lại.");
  }

  private static ApiException tokenError() {
    return new ApiException(
        400,
        "TOKEN_INVALID_OR_EXPIRED",
        "Liên kết không hợp lệ hoặc đã hết hạn. Hãy yêu cầu liên kết mới.");
  }
}
