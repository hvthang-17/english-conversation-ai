package vn.englishconversation.learning.service;

import static vn.englishconversation.learning.dto.LearningData.*;

import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.common.repository.JdbcStore.Row;
import vn.englishconversation.learning.repository.LearningStore;
import vn.englishconversation.lesson.repository.LessonCatalogRepository;

@Service
@Profile("g6 | (!bootstrap & !g1 & !g2 & !g3 & !g4 & !g5)")
public class LearningService {
  private final LearningStore store;
  private final LessonCatalogRepository lessons;

  private final java.util.Optional<vn.englishconversation.listening.service.ListeningAttempts> listening;
  private final java.util.Optional<vn.englishconversation.pronunciation.service.PronunciationService> pronunciation;

  private final java.util.Optional<vn.englishconversation.learning.repository.ProgressRepository> progress;

  public LearningService(LearningStore store, LessonCatalogRepository lessons,
      java.util.Optional<vn.englishconversation.listening.service.ListeningAttempts> listening,
      java.util.Optional<vn.englishconversation.pronunciation.service.PronunciationService> pronunciation,
      java.util.Optional<vn.englishconversation.learning.repository.ProgressRepository> progress) {
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
    return store.cards(owner.id(), status, page, size);
  }

  @Transactional
  public Card save(AccountPrincipal owner, long suggestion) {
    account(owner);
    Row source =
        store
            .suggestion(owner.id(), suggestion)
            .orElseThrow(() -> missing("SUGGESTION_NOT_FOUND", "Không tìm thấy gợi ý để lưu."));
    return store.saveCard(owner.id(), suggestion, source);
  }

  @Transactional
  public Card answer(AccountPrincipal owner, long id, ReviewAnswer answer) {
    account(owner);
    if (!store.hasCard(owner.id(), id))
      throw missing("REVIEW_CARD_NOT_FOUND", "Không tìm thấy thẻ.");
    var prior = store.priorAnswer(id, answer.clientRequestId());
    if (prior.isPresent()) {
      if (!prior.get().text("result").equals(answer.result()))
        throw new ApiException(
            409, "REQUEST_ID_REUSED", "Mã yêu cầu đã được dùng cho kết quả khác.");
    } else {
      store.recordAnswer(owner.id(), id, answer);
    }
    return store.card(owner.id(), id);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<HistoryEntry> history(AccountPrincipal owner, int page, int size) {
    validatePage(page, size);
    return store.history(owner.id(), page, size);
  }

  @Transactional
  public void deleteSession(AccountPrincipal owner, long id) {
    account(owner);
    if (!store.lockSession(owner.id(), id))
      throw missing("PRACTICE_NOT_FOUND", "Không tìm thấy buổi luyện.");
    store.deletePractice(owner.id(), id);
  }

  @Transactional
  public void deleteAll(AccountPrincipal owner) {
    account(owner);
    for (long id : store.lockAllSessions(owner.id())) store.deletePractice(owner.id(), id);
    store.deletePlans(owner.id());
    progress.ifPresent(repository -> repository.clear(owner.id()));
    store.deleteCards(owner.id());
    lessons.removeAll(owner.id());
    listening.ifPresent(service -> service.clear(owner));
    pronunciation.ifPresent(service -> service.clearLearningData(owner.id()));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Statistics statistics(AccountPrincipal owner) {
    return store.statistics(owner.id());
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
