package vn.englishconversation.learning.service;

import static vn.englishconversation.learning.dto.NotebookData.*;

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
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.common.repository.JdbcStore.Row;
import vn.englishconversation.learning.repository.NotebookRepository;
import vn.englishconversation.lesson.repository.LessonMaterialsRepository;
import vn.englishconversation.user.repository.UserRepository;

@Service
@Profile("!bootstrap & !g1 & !g2")
public class NotebookService {
  private static final int DAILY_NEW_LIMIT = 10;
  private final NotebookRepository notebook;
  private final LessonMaterialsRepository materials;
  private final UserRepository users;
  private final Clock clock;
  private final boolean spacedEnabled;

  public NotebookService(NotebookRepository notebook, LessonMaterialsRepository materials, UserRepository users,
      ObjectProvider<Clock> clocks, Environment environment) {
    this.notebook = notebook;
    this.materials = materials;
    this.users = users;
    this.clock = clocks.getIfAvailable(Clock::systemUTC);
    this.spacedEnabled = environment.matchesProfiles("g6");
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<Card> cards(AccountPrincipal owner, Status status, int page, int size) {
    validatePage(page, size);
    return notebook.cards(owner.id(), status, page, size);
  }

  @Transactional(readOnly = true)
  public Card detail(AccountPrincipal owner, long id) {
    return NotebookRepository.card(ownedCard(owner, id));
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
    long newCount = notebook.newCards(user);
    long dueCount = notebook.dueCards(user, current);
    var items = new ArrayList<>(notebook.due(user, current, limit));
    int newLimit = Math.min(remaining, limit - items.size());
    if (newLimit > 0) items.addAll(notebook.newQueue(user, newLimit));
    return new ReviewQueue(dueCount, newCount, Math.min(remaining, newCount), remaining,
        notebook.nextDue(user, current), window.end(), window.zone().getId(), List.copyOf(items));
  }

  @Transactional
  public Card save(AccountPrincipal owner, long sourceId) {
    lockOwner(owner);
    return notebook.saved(owner.id(), sourceId).orElseGet(() -> {
      var item = materials.lockVisibleItem(sourceId)
          .orElseThrow(() -> new ApiException(404, "LANGUAGE_ITEM_NOT_FOUND", "Mục học liệu không còn khả dụng."));
      return notebook.save(owner.id(), sourceId, item);
    });
  }

  @Transactional
  public void delete(AccountPrincipal owner, long cardId) {
    lockOwner(owner);
    ownedCard(owner, cardId);
    notebook.delete(owner.id(), cardId);
  }

  @Transactional
  public void clear(AccountPrincipal owner) {
    lockOwner(owner);
    notebook.clear(owner.id());
  }

  @Transactional
  public Attempt review(AccountPrincipal owner, long cardId, ReviewInput input) {
    lockOwner(owner);
    var card = ownedCard(owner, cardId);
    boolean valid = switch (input.mode()) {
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
    var prior = notebook.prior(cardId, input.clientRequestId());
    if (prior.isPresent()) {
      var attempt = NotebookRepository.attempt(prior.get());
      String previous = prior.get().text("request_fingerprint");
      boolean match = previous == null
          ? input.mode() == Mode.FLASHCARD && attempt.mode() == Mode.FLASHCARD && attempt.result() == input.result()
          : previous.equals(fingerprint);
      if (!match) throw new ApiException(409, "REQUEST_ID_REUSED", "Mã lượt ôn đã được dùng cho câu trả lời khác.");
      return attempt;
    }
    if (input.mode() == Mode.FLASHCARD)
      return notebook.addAttempt(cardId, input, input.result(), null, fingerprint);
    if (spaced) return gradeScheduled(owner.id(), cardId, card, input, fingerprint);

    throw new ApiException(400, "INVALID_REVIEW_ANSWER", "Chế độ ôn không còn được hỗ trợ.");
  }

  private Attempt gradeScheduled(long owner, long cardId, Row card, ReviewInput input, String fingerprint) {
    if (card.number("schedule_version") != input.scheduleVersion())
      throw new ApiException(409, "REVIEW_SCHEDULE_CHANGED", "Lịch thẻ đã thay đổi. Hãy tải lại đợt ôn.");
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var due = (LocalDateTime) card.data().get("next_review_at");
    if (input.scheduleVersion() > 0 && due != null && due.isAfter(utc(now)))
      throw new ApiException(409, "REVIEW_NOT_DUE", "Thẻ chưa đến hạn ôn.");
    if (input.scheduleVersion() == 0 && remainingNew(owner, reviewDay(owner, now)) == 0)
      throw new ApiException(409, "NEW_REVIEW_LIMIT_REACHED", "Đã hết suất thẻ mới hôm nay.");
    var next = SpacedReviewSchedule.next(input.rating(), (int) card.number("spaced_interval_days"), now);
    return notebook.addScheduledAttempt(cardId, input, Status.valueOf(card.text("status")), fingerprint,
        SpacedReviewSchedule.VERSION, next.days(), utc(now), utc(next.dueAt()));
  }

  private record ReviewDay(ZoneId zone, Instant start, Instant end) {}

  private ReviewDay reviewDay(long owner, Instant now) {
    var zone = ZoneId.of(notebook.timeZone(owner));
    var date = now.atZone(zone).toLocalDate();
    return new ReviewDay(zone, date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
  }

  private int remainingNew(long owner, ReviewDay day) {
    return (int) Math.max(0, DAILY_NEW_LIMIT - notebook.firstReviews(owner, utc(day.start()), utc(day.end())));
  }

  private void requireSpaced() {
    if (!spacedEnabled)
      throw new ApiException(404, "SPACED_REVIEW_UNAVAILABLE", "Bản dựng này chưa hỗ trợ ôn theo lịch.");
  }

  private static LocalDateTime utc(Instant value) {
    return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
  }

  private Row ownedCard(AccountPrincipal owner, long cardId) {
    return notebook.card(owner.id(), cardId)
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
