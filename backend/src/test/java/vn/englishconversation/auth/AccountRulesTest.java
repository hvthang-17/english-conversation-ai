package vn.englishconversation.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;

class AccountRulesTest {
  @Test
  void normalizesOnlyEmailAndKeepsPlusDotAndAccentsDistinct() {
    assertEquals(
        "first.last+tag@example.com", AccountService.email("  First.Last+Tag@Example.COM "));
    for (String invalid :
        new String[] {
          "a..b@example.com",
          "a@bad..example.com",
          "a@-bad.com",
          "é@example.com",
          "a@local",
          ".a@example.com",
          "a.@example.com"
        }) assertThrows(ApiException.class, () -> AccountService.email(invalid));
  }

  @Test
  void passwordBoundsDoNotTransformContent() {
    assertDoesNotThrow(() -> AccountService.password("  passphrase with spaces  "));
    assertThrows(ApiException.class, () -> AccountService.password("x".repeat(11)));
    assertThrows(ApiException.class, () -> AccountService.password("x".repeat(129)));
  }

}
