package vn.englishconversation.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.englishconversation.auth.security.TokenCipher;
import vn.englishconversation.auth.service.AccountLifecycleService;
import vn.englishconversation.auth.service.AccountRateLimiter;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.mail.provider.MailGateway;
import vn.englishconversation.user.entity.UserAccount;
import vn.englishconversation.user.repository.UserRepository;

class PasswordResetAvailabilityTest {
  @Test
  void disabledMailAcceptsResetRequestWithoutQueuing() {
    var db = mock(JdbcStore.class);
    var users = mock(UserRepository.class);
    var rates = mock(AccountRateLimiter.class);
    var manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    var user = new UserAccount();
    user.id = 1L;
    user.status = "ACTIVE";
    when(users.lockByEmail("learner@example.test")).thenReturn(java.util.Optional.of(user));
    var service = new AccountLifecycleService(db, rates, users,
        mock(PasswordEncoder.class), mock(JdbcIndexedSessionRepository.class),
        new TokenCipher(""), mock(MailGateway.class), manager, "http://localhost:5173");

    assertDoesNotThrow(() -> service.requestReset("Learner@example.test"));
    verify(rates).check("reset:learner@example.test", 5, 60);
    verifyNoInteractions(db);
  }
}
