package vn.englishconversation.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import vn.englishconversation.admin.repository.AdminAudit;
import vn.englishconversation.auth.service.AccountLifecycleService;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.profile.service.ProfileService;
import vn.englishconversation.user.repository.UserRepository;

class RegistrationConflictTest {
  @Test
  void concurrentEmailInsertMapsOnlyTheEmailConstraint() {
    for (String name : new String[] {"uk_users_email", "users.uk_users_email", "`users`.`uk_users_email`"}) {
      var users = mock(UserRepository.class);
      var profiles = mock(ProfileService.class);
      var failure = violation(name);
      when(users.saveAndFlush(any())).thenThrow(failure);
      var service = service(users, profiles);
      var result = assertThrows(ApiException.class,
          () -> service.register(" Learner@Example.com ", "test-password-long"));
      assertEquals(409, result.status);
      assertEquals("ACCOUNT_CONFLICT", result.code);
      verify(profiles, never()).createDefault(anyLong());
    }
  }

  @Test
  void unrelatedAndUnknownIntegrityErrorsAreNotEmailConflicts() {
    for (String name : new String[] {"fk_users_other", "uk_other_email", null}) {
      var users = mock(UserRepository.class);
      var failure = violation(name);
      when(users.saveAndFlush(any())).thenThrow(failure);
      var service = service(users, mock(ProfileService.class));
      assertSame(failure, assertThrows(DataIntegrityViolationException.class,
          () -> service.register("learner@example.com", "test-password-long")));
    }
  }

  private static DataIntegrityViolationException violation(String constraint) {
    return new DataIntegrityViolationException("persistence failure",
        new org.hibernate.exception.ConstraintViolationException("constraint failure",
            new SQLException("synthetic", "23000", 1062), constraint));
  }

  private static AccountService service(UserRepository users, ProfileService profiles) {
    var encoder = mock(PasswordEncoder.class);
    when(encoder.encode(anyString())).thenReturn("synthetic-hash");
    return new AccountService(users, encoder, mock(JdbcIndexedSessionRepository.class),
        profiles, mock(AdminAudit.class), mock(AccountLifecycleService.class));
  }
}
