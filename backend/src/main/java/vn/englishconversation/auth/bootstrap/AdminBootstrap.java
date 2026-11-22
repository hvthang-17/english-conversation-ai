package vn.englishconversation.auth.bootstrap;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.user.entity.UserAccount;
import vn.englishconversation.user.repository.UserRepository;

@Component
@Profile("!bootstrap")
public class AdminBootstrap implements ApplicationRunner {
  private final boolean enabled;
  private final String email, password;
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;

  public AdminBootstrap(
      @Value("${app.admin-bootstrap.enabled}") boolean enabled,
      @Value("${app.admin-bootstrap.email}") String email,
      @Value("${app.admin-bootstrap.password}") String password,
      UserRepository users,
      PasswordEncoder encoder,
      JdbcTemplate jdbc,
      PlatformTransactionManager manager) {
    this.enabled = enabled;
    this.email = email;
    this.password = password;
    this.users = users;
    this.encoder = encoder;
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(manager);
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled) return;
    jdbc.execute(
        (org.springframework.jdbc.core.ConnectionCallback<Void>)
            connection -> {
              try (var lock =
                  connection.prepareStatement(
                      "SELECT GET_LOCK('english_conversation_admin_bootstrap', 10)")) {
                try (var result = lock.executeQuery()) {
                  if (!result.next() || result.getInt(1) != 1)
                    throw new IllegalStateException("Admin bootstrap lock unavailable");
                }
              }
              try {
                transaction.executeWithoutResult(
                    tx -> {
                      if (users.existsByRole("ADMIN")) return;
                      String normalized = AccountService.email(email);
                      AccountService.password(password);
                      if (users.findByEmail(normalized).isPresent())
                        throw new IllegalStateException(
                            "Bootstrap email already belongs to an account; no changes made");
                      UserAccount u = new UserAccount();
                      u.email = normalized;
                      u.passwordHash = encoder.encode(password);
                      u.role = "ADMIN";
                      u.status = "ACTIVE";
                      u.createdAt = u.updatedAt = Instant.now();
                      users.saveAndFlush(u);
                    });
              } finally {
                try (var release =
                    connection.prepareStatement(
                        "SELECT RELEASE_LOCK('english_conversation_admin_bootstrap')")) {
                  release.execute();
                }
              }
              return null;
            });
  }
}
