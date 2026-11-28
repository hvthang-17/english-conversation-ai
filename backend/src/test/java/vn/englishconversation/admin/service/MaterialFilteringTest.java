package vn.englishconversation.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.englishconversation.admin.dto.MaterialKind;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.common.repository.JdbcStore;

class MaterialFilteringTest {
  @Test
  void omittedFilterPreservesCombinedListing() {
    var db = mock(JdbcStore.class);
    when(db.rows("SELECT * FROM language_items ORDER BY id DESC LIMIT ? OFFSET ?", 12, 0L)).thenReturn(List.of());
    when(db.count("SELECT COUNT(*) FROM language_items")).thenReturn(30L);
    var service = new MaterialAdminService(new vn.englishconversation.admin.repository.MaterialAdminRepository(db), null, null, null);
    assertEquals(30L, service.list(MaterialKind.WORDS, 0, 12).total());
    verify(db).rows("SELECT * FROM language_items ORDER BY id DESC LIMIT ? OFFSET ?", 12, 0L);
    verify(db).count("SELECT COUNT(*) FROM language_items");
  }

  @Test
  void filtersRowsAndCountBeforePaginationForBothExpressionKinds() {
    for (String kind : List.of("WORD", "PHRASE")) {
      var db = mock(JdbcStore.class);
      String rows = "SELECT * FROM language_items WHERE kind=? ORDER BY id DESC LIMIT ? OFFSET ?";
      String count = "SELECT COUNT(*) FROM language_items WHERE kind=?";
      when(db.rows(rows, kind, 12, 12L)).thenReturn(List.of());
      when(db.count(count, kind)).thenReturn(25L);
      var service = new MaterialAdminService(new vn.englishconversation.admin.repository.MaterialAdminRepository(db), null, null, null);
      assertEquals(25L, service.list(MaterialKind.WORDS, 1, 12, kind).total());
      verify(db).rows(rows, kind, 12, 12L);
      verify(db).count(count, kind);
    }
  }

  @Test
  void rejectsUnsupportedFilterWithoutQueryingDatabase() {
    var db = mock(JdbcStore.class);
    var service = new MaterialAdminService(new vn.englishconversation.admin.repository.MaterialAdminRepository(db), null, null, null);
    assertThrows(ApiException.class, () -> service.list(MaterialKind.WORDS, 0, 12, "INVALID"));
    assertThrows(ApiException.class, () -> service.list(MaterialKind.LESSONS, 0, 12, "WORD"));
    verifyNoInteractions(db);
  }
}
