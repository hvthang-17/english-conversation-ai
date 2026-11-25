package vn.englishconversation.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.admin.repository.AdminAudit;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.profile.service.ProfileService;
import vn.englishconversation.user.dto.UserResponse;
import vn.englishconversation.user.entity.UserAccount;
import vn.englishconversation.user.repository.UserRepository;

@Service
@Profile("!bootstrap")
public class AccountService {
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JdbcIndexedSessionRepository sessions;
  private final String dummy;
  private final ProfileService profiles;
  private final AdminAudit audit;
  private final vn.englishconversation.auth.service.AccountLifecycleService lifecycle;

  public AccountService(
      UserRepository users,
      PasswordEncoder encoder,
      JdbcIndexedSessionRepository sessions,
      ProfileService profiles,
      AdminAudit audit, vn.englishconversation.auth.service.AccountLifecycleService lifecycle) {
    this.lifecycle=lifecycle;
    this.profiles = profiles;
    this.audit = audit;
    this.users = users;
    this.encoder = encoder;
    this.sessions = sessions;
    this.dummy = encoder.encode("timing-only-non-account-password");
  }

  public static String email(String raw) {
    String value = raw.strip().toLowerCase(Locale.ROOT);
    int at = value.lastIndexOf('@');
    boolean localValid =
        at > 0
            && at <= 64
            && !value.startsWith(".")
            && value.charAt(at - 1) != '.'
            && !value.substring(0, Math.max(at, 0)).contains("..");
    boolean syntaxValid =
        value.matches(
            "[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}");
    if (value.length() > 254 || !localValid || !syntaxValid) {
      throw new ApiException(400, "INVALID_EMAIL", "Email không hợp lệ; sử dụng địa chỉ ASCII.");
    }
    return value;
  }

  public static void password(String value) {
    if (value.length() < 12 || value.length() > 128)
      throw new ApiException(400, "INVALID_PASSWORD", "Mật khẩu cần từ 12 đến 128 ký tự.");
  }

  @Transactional
  public UserResponse register(String raw, String password) {
    String normalized = email(raw);
    password(password);
    if (users.findByEmail(normalized).isPresent())
      throw new ApiException(409, "ACCOUNT_CONFLICT", "Không thể tạo tài khoản với email này.");
    UserAccount u = new UserAccount();
    u.email = normalized;
    u.passwordHash = encoder.encode(password);
    u.role = "LEARNER";
    u.status = "ACTIVE";
    u.createdAt = u.updatedAt = Instant.now();
    users.saveAndFlush(u);
    profiles.createDefault(u.id);
    return view(u);
  }

  @Transactional
  public UsernamePasswordAuthenticationToken login(String raw, String password) {
    UserAccount found = users.lockByEmail(email(raw)).orElse(null);
    if (found == null) {
      encoder.matches(password, dummy);
      throw invalid();
    }
    UserAccount u = found;
    if (!encoder.matches(password, u.passwordHash) || (!u.status.equals("ACTIVE") || u.deletionRequestedAt!=null)) throw invalid();
    u.lastLoginAt = u.updatedAt = Instant.now();
    return UsernamePasswordAuthenticationToken.authenticated(
        new AccountPrincipal(u.id, u.securityVersion),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + u.role)));
  }

  @Transactional
  public void changePassword(AccountPrincipal p, String current, String next) {
    password(next);
    UserAccount u = users.lockById(p.id()).orElseThrow(AccountService::invalid);
    if (!valid(u, p) || !encoder.matches(current, u.passwordHash))
      throw new ApiException(400, "CURRENT_PASSWORD_INVALID", "Mật khẩu hiện tại không đúng.");
    u.passwordHash = encoder.encode(next);
    u.securityVersion++;
    u.passwordChangedAt = u.updatedAt = Instant.now();
    lifecycle.invalidateReset(u.id);
    users.flush();
    revoke(u.id);
  }

  @Transactional
  public UserResponse changeAccess(long id, String role, String status) {
    return updateAccess(lockTarget(id), role, status);
  }

  @Transactional
  public UserResponse changeAccess(AccountPrincipal actor, long id, String role, String status) {
    if (id == actor.id()) {
      lockAdmin(actor);
      throw new ApiException(409, "ADMIN_SELF_ACCESS", "Hãy nhờ quản trị viên khác thay đổi quyền hoặc trạng thái của bạn.");
    }
    if (role == null && status == null)
      throw new ApiException(400, "ACCESS_CHANGE_REQUIRED", "Hãy chọn quyền hoặc trạng thái cần đổi.");
    UserAccount target;
    if (id < actor.id()) {
      target = lockTarget(id);
      lockAdmin(actor);
    } else {
      lockAdmin(actor);
      target = lockTarget(id);
    }
    if (target.deletionRequestedAt != null)
      throw new ApiException(409, "ACCOUNT_DELETION_PENDING", "Tài khoản đang chờ xóa.");
    String previousRole = target.role;
    String previousStatus = target.status;
    UserResponse result = updateAccess(target, role, status);
    if (!previousRole.equals(result.role()) || !previousStatus.equals(result.status()))
      audit.account(actor.id(), id, previousRole, previousStatus, result.role(), result.status());
    return result;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void requireAdminForWrite(AccountPrincipal actor) {
    lockAdmin(actor);
  }

  private UserAccount lockAdmin(AccountPrincipal actor) {
    return users.lockById(actor.id())
        .filter(user -> valid(user, actor) && "ADMIN".equals(user.role))
        .orElseThrow(() -> new ApiException(403, "ADMIN_REQUIRED", "Cần quyền quản trị."));
  }

  private UserAccount lockTarget(long id) {
    return users.lockById(id)
        .orElseThrow(() -> new ApiException(404, "USER_NOT_FOUND", "Không tìm thấy tài khoản."));
  }

  private UserResponse updateAccess(UserAccount u, String role, String status) {
    if (role == null) role = u.role;
    if (status == null) status = u.status;
    if (!u.role.equals(role) || !u.status.equals(status)) {
      u.role = role;
      u.status = status;
      u.securityVersion++;
      u.updatedAt = Instant.now();
      users.flush();
      revoke(u.id);
    }
    return view(u);
  }

  public void revoke(long id) {
    sessions.findByPrincipalName(Long.toString(id)).keySet().forEach(sessions::deleteById);
  }

  public static boolean valid(UserAccount u, AccountPrincipal p) {
    return u.deletionRequestedAt==null && u.status.equals("ACTIVE") && u.securityVersion == p.securityVersion();
  }

  public UserResponse current(long id) {
    return view(users.findById(id).orElseThrow(AccountService::invalid));
  }

  static UserResponse view(UserAccount u) {
    return new UserResponse(u.id.toString(), u.email, u.role, u.status);
  }

  static ApiException invalid() {
    return new ApiException(
        401,
        "INVALID_CREDENTIALS",
        "Email hoặc mật khẩu không đúng, hoặc tài khoản không khả dụng.");
  }
}
