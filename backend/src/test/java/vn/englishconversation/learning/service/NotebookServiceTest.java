package vn.englishconversation.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static vn.englishconversation.learning.dto.NotebookData.*;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.common.repository.JdbcStore.Row;
import vn.englishconversation.learning.repository.NotebookRepository;
import vn.englishconversation.lesson.repository.LessonMaterialsRepository;
import vn.englishconversation.user.entity.UserAccount;
import vn.englishconversation.user.repository.UserRepository;

class NotebookServiceTest {
  private final NotebookRepository repository = mock(NotebookRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final AccountPrincipal owner = new AccountPrincipal(7L, 0);
  private NotebookService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() {
    service = new NotebookService(repository, mock(LessonMaterialsRepository.class), users,
        mock(ObjectProvider.class), mock(Environment.class));
    var account = new UserAccount();
    account.id = owner.id();
    account.status = "ACTIVE";
    when(users.lockById(owner.id())).thenReturn(Optional.of(account));
    when(repository.card(owner.id(), 10L)).thenReturn(Optional.of(new Row(Map.of("status", "NEEDS_REVIEW"))));
  }

  @Test
  void rejectsRetiredQuizWithoutWritingAttempts() {
    var input = new ReviewInput(Mode.QUICK_QUIZ, null, "old-quiz", "answer", null, null);
    assertEquals("INVALID_REVIEW_ANSWER", assertThrows(ApiException.class, () -> service.review(owner, 10L, input)).code);
    verify(repository, never()).prior(anyLong(), anyString());
    verify(repository, never()).addAttempt(anyLong(), any(), any(), any(), anyString());
  }

  @Test
  void stillWritesFlashcardReviewsForReviewTab() {
    var input = new ReviewInput(Mode.FLASHCARD, Status.REMEMBERED, "review-1", null, null, null);
    var saved = new Attempt("1", Mode.FLASHCARD, Status.REMEMBERED, null, "now", null, null, null, null, null);
    when(repository.prior(10L, "review-1")).thenReturn(Optional.empty());
    when(repository.addAttempt(eq(10L), eq(input), eq(Status.REMEMBERED), isNull(), anyString())).thenReturn(saved);
    assertSame(saved, service.review(owner, 10L, input));
  }

  @Test
  void rejectsAnotherOwnersCardBeforeWriting() {
    when(repository.card(owner.id(), 10L)).thenReturn(Optional.empty());
    var input = new ReviewInput(Mode.FLASHCARD, Status.REMEMBERED, "review-1", null, null, null);
    assertEquals(404, assertThrows(ApiException.class, () -> service.review(owner, 10L, input)).status);
    verify(repository, never()).prior(anyLong(), anyString());
    verify(repository, never()).addAttempt(anyLong(), any(), any(), any(), anyString());
  }

  @Test
  void retryingSameFlashcardDoesNotCreateAnotherAttempt() {
    var input = new ReviewInput(Mode.FLASHCARD, Status.REMEMBERED, "review-1", null, null, null);
    when(repository.prior(10L, "review-1")).thenReturn(Optional.of(new Row(Map.of(
        "id", 1L, "review_mode", "FLASHCARD", "result", "REMEMBERED", "reviewed", "now"))));
    assertEquals("1", service.review(owner, 10L, input).id());
    verify(repository, never()).addAttempt(anyLong(), any(), any(), any(), anyString());
  }

}
