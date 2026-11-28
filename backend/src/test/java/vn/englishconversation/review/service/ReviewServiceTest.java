package vn.englishconversation.review.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static vn.englishconversation.review.model.ReviewData.*;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.common.exception.ApiException;

import vn.englishconversation.review.repository.ReviewRepository;
import vn.englishconversation.lesson.repository.LessonMaterialsRepository;
import vn.englishconversation.user.entity.UserAccount;
import vn.englishconversation.user.repository.UserRepository;

class ReviewServiceTest {
  private final ReviewRepository repository = mock(ReviewRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final AccountPrincipal owner = new AccountPrincipal(7L, 0);
  private ReviewService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() {
    service = new ReviewService(repository, mock(LessonMaterialsRepository.class), users,
        mock(ObjectProvider.class), new vn.englishconversation.config.features.FeatureSet(vn.englishconversation.config.features.FeatureProperties.Stage.MATERIALS), mock(vn.englishconversation.feedback.repository.FeedbackStore.class));
    var account = new UserAccount();
    account.id = owner.id();
    account.status = "ACTIVE";
    when(users.lockById(owner.id())).thenReturn(Optional.of(account));
    when(repository.card(owner.id(), 10L)).thenReturn(Optional.of(new ReviewRepository.CardState(null,0,0,null,"NEEDS_REVIEW")));
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
    when(repository.prior(10L, "review-1")).thenReturn(Optional.of(new ReviewRepository.PriorState(new Attempt("1",Mode.FLASHCARD,Status.REMEMBERED,null,"now",null,null,null,null,null),null)));
    assertEquals("1", service.review(owner, 10L, input).id());
    verify(repository, never()).addAttempt(anyLong(), any(), any(), any(), anyString());
  }

}
