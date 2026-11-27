package vn.englishconversation.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import vn.englishconversation.admin.repository.AdminActivityStore;
import vn.englishconversation.common.exception.ApiException;

class AdminActivityServiceTest {
  @Test
  void validatesPageAndFiltersBeforeReading() {
    var store = mock(AdminActivityStore.class);
    var service = new AdminActivityService(store);
    assertThrows(ApiException.class, () -> service.list(true, -1, 12, "", ""));
    assertThrows(ApiException.class, () -> service.list(false, 0, 51, "", ""));
    assertThrows(ApiException.class, () -> service.list(false, 0, 0, "", ""));
    assertThrows(ApiException.class, () -> service.list(false, 0, 12, "x".repeat(121), ""));
    assertThrows(ApiException.class, () -> service.list(false, 0, 12, "", "OTHER"));
    assertThrows(ApiException.class, () -> service.list(true, 0, 12, "", "FAILED"));
    verifyNoInteractions(store);
    service.list(false, 2, 24, "  Gemini  ", "FAILED");
    verify(store).list(false, 2, 24, "Gemini", "FAILED");
  }
}
