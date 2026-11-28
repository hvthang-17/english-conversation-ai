package vn.englishconversation.review.service;

import static vn.englishconversation.review.model.ReviewData.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.review.repository.ReviewRepository.CardState;
import vn.englishconversation.review.repository.ReviewRepository;
import vn.englishconversation.lesson.repository.LessonMaterialsRepository;
import vn.englishconversation.user.repository.UserRepository;

@Service
@FeatureEnabled(FeatureEnabled.Feature.MATERIALS)
public class ReviewService {
  private static final int DAILY_NEW_LIMIT = 10;
  private final ReviewRepository repository;
  private final LessonMaterialsRepository materials;
  private final UserRepository users;
  private final vn.englishconversation.feedback.repository.FeedbackStore feedback;
  private final Clock clock;
  private final boolean spacedEnabled;

  public ReviewService(ReviewRepository repository, LessonMaterialsRepository materials, UserRepository users,
      ObjectProvider<Clock> clocks, vn.englishconversation.config.features.FeatureSet features,
      vn.englishconversation.feedback.repository.FeedbackStore feedback) {
    this.repository = repository;
    this.feedback = feedback;
    this.materials = materials;
    this.users = users;
    this.clock = clocks.getIfAvailable(Clock::systemUTC);
    this.spacedEnabled = features.enabled(vn.englishconversation.config.features.FeatureEnabled.Feature.SPACED_REVIEW);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<Card> cards(AccountPrincipal owner, Status status, int page, int size) {
    validatePage(page, size);
    return repository.cards(owner.id(), status, page, size, false);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<Card> legacyCards(AccountPrincipal owner, Status status, int page, int size) {
    if (page < 0 || page > 1000000 || size < 1 || size > 50)
      throw new ApiException(400, "INVALID_PAGINATION", "Trang hoặc kích thước trang không hợp lệ.");
    return repository.cards(owner.id(), status, page, size, true);
  }

  @Transactional
  public Card saveSuggestion(AccountPrincipal owner, long suggestionId) {
    lockOwner(owner);
    var source = feedback.suggestion(owner.id(), suggestionId)
        .orElseThrow(() -> new ApiException(404, "SUGGESTION_NOT_FOUND", "Không tìm thấy gợi ý để lưu."));
    if ("false".equals(source.meaningVerified()))
      throw new ApiException(409, "SUGGESTION_MEANING_UNVERIFIED", "Nghĩa của gợi ý này chưa được đối chiếu với học liệu.");
    return repository.savedSuggestion(owner.id(), suggestionId).orElseGet(() -> repository.save(owner.id(),
        new ReviewRepository.Snapshot(suggestionId, null, source.itemType(), source.expression(),
            source.meaningVi(), source.exampleEn(), null, source.partOfSpeech())));
  }

  @Transactional(readOnly = true)
  public Card detail(AccountPrincipal owner, long id) {
    return ReviewRepository.card(ownedCard(owner, id));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReviewQueue queue(AccountPrincipal owner, int limit) {
    requireSpaced();
    if (limit < 1 || limit > 20)
      throw new ApiException(400, "INVALID_PAGINATION", "Mỗi đợt cần từ 1 đến 20 thẻ.");
    Instant now = clock.instant();
    LocalDateTime current = utc(now);
    long user = owner.id();
    var window = reviewDay(user, now);
    int remaining = remainingNew(user, window);
    long newCount = repository.newCards(user);
    long dueCount = repository.dueCards(user, current);
    var items = new ArrayList<>(repository.due(user, current, limit));
    int newLimit = Math.min(remaining, limit - items.size());
    if (newLimit > 0) items.addAll(repository.newQueue(user, newLimit));
    return new ReviewQueue(dueCount, newCount, Math.min(remaining, newCount), remaining,
        repository.nextDue(user, current), window.end(), window.zone().getId(), List.copyOf(items));
  }

  @Transactional
  public Card save(AccountPrincipal owner, long sourceId) {
    lockOwner(owner);
    return repository.saved(owner.id(), sourceId).orElseGet(() -> {
      var item = materials.lockVisibleItem(sourceId)
          .orElseThrow(() -> new ApiException(404, "LANGUAGE_ITEM_NOT_FOUND", "Mục học liệu không còn khả dụng."));
      return repository.save(owner.id(), new ReviewRepository.Snapshot(null, sourceId,
          "WORD".equals(item.kind()) ? "VOCABULARY" : "PHRASE", item.expression(), item.meaningVi(),
          item.exampleEn(), item.ipa(), item.partOfSpeech()));
    });
  }

  @Transactional
  public void delete(AccountPrincipal owner, long cardId) {
    lockOwner(owner);
    ownedCard(owner, cardId);
    repository.delete(owner.id(), cardId);
  }

  @Transactional
  public void clear(AccountPrincipal owner) {
    lockOwner(owner);
    repository.clear(owner.id());
  }

  @Transactional
  public Attempt review(AccountPrincipal owner, long cardId, ReviewInput input) {
    lockOwner(owner);
    var card = ownedCard(owner, cardId);
    boolean valid = input.mode() != null && switch (input.mode()) {
      case FLASHCARD -> input.result() != null && input.optionId() == null
          && input.rating() == null && input.scheduleVersion() == null;
      case QUICK_QUIZ -> false; // Historical rows remain readable; new quizzes are retired.
      case SPACED_REVIEW -> input.result() == null && input.optionId() == null
          && input.rating() != null && input.scheduleVersion() != null && input.scheduleVersion() >= 0;
    };
    if (!valid)
      throw new ApiException(400, "INVALID_REVIEW_ANSWER", "Thông tin lượt ôn không hợp lệ.");
    boolean spaced = input.mode() == Mode.SPACED_REVIEW;
    if (spaced) requireSpaced();
    String fingerprint = fingerprint(spaced
        ? input.mode().name() + ":" + input.rating() + ":" + input.scheduleVersion()
        : input.mode().name() + ":" + input.result() + ":" + input.optionId());
    var prior = repository.prior(cardId, input.clientRequestId());
    if (prior.isPresent()) {
      var attempt = ReviewRepository.attempt(prior.get());
      String previous = prior.get().requestFingerprint();
      boolean match = previous == null
          ? input.mode() == Mode.FLASHCARD && attempt.mode() == Mode.FLASHCARD && attempt.result() == input.result()
          : previous.equals(fingerprint);
      if (!match) throw new ApiException(409, "REQUEST_ID_REUSED", "Mã lượt ôn đã được dùng cho câu trả lời khác.");
      return attempt;
    }
    if (input.mode() == Mode.FLASHCARD)
      return repository.addAttempt(cardId, input, input.result(), null, fingerprint);
    if (spaced) return gradeScheduled(owner.id(), cardId, card, input, fingerprint);

    throw new ApiException(400, "INVALID_REVIEW_ANSWER", "Chế độ ôn không còn được hỗ trợ.");
  }

  private Attempt gradeScheduled(long owner, long cardId, CardState card, ReviewInput input, String fingerprint) {
    if (card.scheduleVersion() != input.scheduleVersion())
      throw new ApiException(409, "REVIEW_SCHEDULE_CHANGED", "Lịch thẻ đã thay đổi. Hãy tải lại đợt ôn.");
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var due = card.nextReviewAt();
    if (input.scheduleVersion() > 0 && due != null && due.isAfter(utc(now)))
      throw new ApiException(409, "REVIEW_NOT_DUE", "Thẻ chưa đến hạn ôn.");
    if (input.scheduleVersion() == 0 && remainingNew(owner, reviewDay(owner, now)) == 0)
      throw new ApiException(409, "NEW_REVIEW_LIMIT_REACHED", "Đã hết suất thẻ mới hôm nay.");
    var next = SpacedReviewSchedule.next(input.rating(), (int) card.spacedIntervalDays(), now);
    return repository.addScheduledAttempt(cardId, input, Status.valueOf(card.status()), fingerprint,
        SpacedReviewSchedule.VERSION, next.days(), utc(now), utc(next.dueAt()));
  }

  private record ReviewDay(ZoneId zone, Instant start, Instant end) {}

  private ReviewDay reviewDay(long owner, Instant now) {
    var zone = ZoneId.of(repository.timeZone(owner));
    var date = now.atZone(zone).toLocalDate();
    return new ReviewDay(zone, date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
  }

  private int remainingNew(long owner, ReviewDay day) {
    return (int) Math.max(0, DAILY_NEW_LIMIT - repository.firstReviews(owner, utc(day.start()), utc(day.end())));
  }

  private void requireSpaced() {
    if (!spacedEnabled)
      throw new ApiException(404, "SPACED_REVIEW_UNAVAILABLE", "Bản dựng này chưa hỗ trợ ôn theo lịch.");
  }

  private static LocalDateTime utc(Instant value) {
    return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
  }

  private CardState ownedCard(AccountPrincipal owner, long cardId) {
    return repository.card(owner.id(), cardId)
        .orElseThrow(() -> new ApiException(404, "REVIEW_CARD_NOT_FOUND", "Không tìm thấy thẻ."));
  }

  private void lockOwner(AccountPrincipal owner) {
    users.lockById(owner.id())
        .filter(user -> AccountService.valid(user, owner))
        .orElseThrow(() -> new ApiException(401, "SESSION_REVOKED", "Vui lòng đăng nhập lại."));
  }

  private static void validatePage(int page, int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 50)
      throw new ApiException(400, "INVALID_PAGINATION", "Trang hoặc kích thước trang không hợp lệ.");
  }

  private static String fingerprint(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }
}
