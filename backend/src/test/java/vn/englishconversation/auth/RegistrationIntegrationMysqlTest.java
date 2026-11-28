package vn.englishconversation.auth;


import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.conversation.dto.*;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class RegistrationIntegrationMysqlTest extends FullSystemMysqlFixture {

  @Test
  void concurrentRegistrationsPreserveEmailConflictContract() throws Exception {
    String email = "race-" + java.util.UUID.randomUUID() + "@example.com";
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      java.util.concurrent.Callable<String> register = () -> {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Registration start timeout");
        try {
          accounts.register(" " + email.toUpperCase(java.util.Locale.ROOT) + " ", "test-password-long");
          return "CREATED";
        } catch (ApiException conflict) {
          assertEquals(409, conflict.status);
          return conflict.code;
        }
      };
      var first = executor.submit(register);
      var second = executor.submit(register);
      try {
        assertTrue(ready.await(5, TimeUnit.SECONDS));
      } finally {
        start.countDown();
      }
      assertEquals(java.util.Set.of("CREATED", "ACCOUNT_CONFLICT"),
          java.util.Set.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=?", Integer.class, email));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM learner_profiles p JOIN users u ON u.id=p.user_id WHERE u.email=?",
          Integer.class, email));
    }
  }
}
