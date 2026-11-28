package vn.englishconversation.learning.service;

import static vn.englishconversation.learning.dto.LearningData.*;

import java.util.Set;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.learning.repository.LearningStore;
import vn.englishconversation.lesson.repository.LessonCatalogRepository;

@Service
@FeatureEnabled(FeatureEnabled.Feature.LEARNING)
public class LearningService {
  private final LearningStore store;
  private final vn.englishconversation.conversation.service.ConversationHistoryService history;
  private final vn.englishconversation.statistics.service.LearningStatisticsService statistics;
  private final vn.englishconversation.review.service.ReviewService reviews;
  private final LessonCatalogRepository lessons;

  private final vn.englishconversation.listening.service.ListeningAttempts listening;
  private final vn.englishconversation.pronunciation.service.PronunciationService pronunciation;

  private final vn.englishconversation.learning.repository.ProgressRepository progress;

  public LearningService(LearningStore store, LessonCatalogRepository lessons,
      vn.englishconversation.listening.service.ListeningAttempts listening,
      vn.englishconversation.pronunciation.service.PronunciationService pronunciation,
      vn.englishconversation.learning.repository.ProgressRepository progress,
      vn.englishconversation.review.service.ReviewService reviews, vn.englishconversation.conversation.service.ConversationHistoryService history, vn.englishconversation.statistics.service.LearningStatisticsService statistics) {
    this.history=history; this.statistics=statistics;
    this.reviews = reviews;
    this.listening = listening;
    this.pronunciation = pronunciation;
    this.progress = progress;
    this.store = store;
    this.lessons = lessons;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<Card> cards(AccountPrincipal owner, String status, int page, int size) {
    validatePage(page, size);
    if (status != null && !Set.of("NEEDS_REVIEW", "REMEMBERED").contains(status))
      throw new ApiException(400, "INVALID_REVIEW_STATUS", "Trạng thái ôn tập không hợp lệ.");
    var result = reviews.legacyCards(owner, status == null ? null :
        vn.englishconversation.review.model.ReviewData.Status.valueOf(status), page, size);
    return new Page<>(result.items().stream().map(ReviewViews::legacy).toList(), page, size, result.total());
  }

  @Transactional
  public Card save(AccountPrincipal owner, long suggestion) {
    return ReviewViews.legacy(reviews.saveSuggestion(owner, suggestion));
  }

  @Transactional
  public Card answer(AccountPrincipal owner, long id, ReviewAnswer answer) {
    reviews.review(owner, id, new vn.englishconversation.review.model.ReviewData.ReviewInput(
        vn.englishconversation.review.model.ReviewData.Mode.FLASHCARD,
        vn.englishconversation.review.model.ReviewData.Status.valueOf(answer.result()),
        answer.clientRequestId(), null, null, null));
    return ReviewViews.legacy(reviews.detail(owner, id));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<HistoryEntry> history(AccountPrincipal owner, int page, int size) {
    validatePage(page, size);
    return history.history(owner, page, size);
  }

  @Transactional
  public void deleteSession(AccountPrincipal owner, long id) {
    history.delete(owner,id);
  }

  @Transactional
  public void deleteAll(AccountPrincipal owner) {
    account(owner);
    history.clearLocked(owner.id());
    store.deletePlans(owner.id());
    progress.clear(owner.id());
    reviews.clear(owner);
    lessons.removeAll(owner.id());
    listening.clear(owner);
    pronunciation.clearLearningData(owner.id());
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Statistics statistics(AccountPrincipal owner) {
    return statistics.statistics(owner);
  }

  private void account(AccountPrincipal owner) {
    if (!store.lockAccount(owner.id(), owner.securityVersion()))
      throw new ApiException(401, "SESSION_REVOKED", "Vui lòng đăng nhập lại.");
  }

  private static void validatePage(int page, int size) {
    if (page < 0 || page > 1000000 || size < 1 || size > 50)
      throw new ApiException(
          400, "INVALID_PAGINATION", "Trang hoặc kích thước trang không hợp lệ.");
  }

  private static ApiException missing(String code, String message) {
    return new ApiException(404, code, message);
  }
}
