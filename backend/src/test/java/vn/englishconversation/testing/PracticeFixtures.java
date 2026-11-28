package vn.englishconversation.testing;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.conversation.dto.CreatePracticeRequest;

/** Test accounts and a reviewed A2 scenario; all content is synthetic. */
public final class PracticeFixtures {
  private PracticeFixtures() {}

  public static AccountPrincipal learner(AccountService accounts) {
    String email = "chat-" + UUID.randomUUID() + "@example.test";
    long id = Long.parseLong(accounts.register(email, "Fixture account secret only").id());
    return new AccountPrincipal(id, 0);
  }

  public static CreatePracticeRequest draft(JdbcTemplate jdbc) {
    return new CreatePracticeRequest(jdbc.queryForObject(
        "SELECT CAST(id AS CHAR) FROM scenarios WHERE code='CLUB_INTRO'", String.class),
        "A2", UUID.randomUUID().toString());
  }
}
