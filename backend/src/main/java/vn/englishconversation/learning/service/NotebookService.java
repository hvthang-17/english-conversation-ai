package vn.englishconversation.learning.service;

import static vn.englishconversation.learning.dto.NotebookData.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
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
  private final NotebookRepository notebook;
  private final LessonMaterialsRepository materials;
  private final UserRepository users;

  public NotebookService(NotebookRepository notebook, LessonMaterialsRepository materials, UserRepository users) {
    this.notebook = notebook;
    this.materials = materials;
    this.users = users;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<Card> cards(AccountPrincipal owner, Status status, int page, int size) {
    validatePage(page, size);
    return notebook.cards(owner.id(), status, page, size);
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

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<Attempt> history(AccountPrincipal owner, long cardId, int page, int size) {
    validatePage(page, size);
    ownedCard(owner, cardId);
    return notebook.history(cardId, page, size);
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

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public QuizState quiz(AccountPrincipal owner, long cardId) {
    var card = ownedCard(owner, cardId);
    String answer = card.text("meaning_vi");
    String kind = "VOCABULARY".equals(card.text("item_type")) ? "WORD" : "PHRASE";
    String sourceId = card.text("source_language_item_id");
    var seen = new HashSet<>(List.of(meaningKey(answer)));
    var options = new ArrayList<QuizOption>();
    for (String meaning : materials.quizMeanings(kind, sourceId == null ? null : Long.valueOf(sourceId))) {
      if (seen.add(meaningKey(meaning))) options.add(new QuizOption(UUID.randomUUID().toString(), meaning));
      if (options.size() == 2) break;
    }
    if (options.size() < 2)
      throw new ApiException(409, "QUIZ_UNAVAILABLE", "Chưa đủ lựa chọn phù hợp. Bạn vẫn có thể ôn bằng flashcard.");
    String answerId = UUID.randomUUID().toString();
    options.add(new QuizOption(answerId, answer));
    Collections.shuffle(options);
    return new QuizState(owner.id(), cardId, UUID.randomUUID().toString(), card.text("expression"),
        List.copyOf(options), answerId, Instant.now().plusSeconds(600));
  }

  @Transactional
  public Attempt review(AccountPrincipal owner, long cardId, ReviewInput input, QuizState question) {
    lockOwner(owner);
    var card = ownedCard(owner, cardId);
    if (input.mode() == Mode.FLASHCARD && (input.result() == null || input.optionId() != null)
        || input.mode() == Mode.QUICK_QUIZ && (input.result() != null || input.optionId() == null))
      throw new ApiException(400, "INVALID_REVIEW_ANSWER", "Thông tin lượt ôn không hợp lệ.");
    String fingerprint = fingerprint(input.mode().name() + ":" + input.result() + ":" + input.optionId());
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

    if (question == null || question.ownerId() != owner.id() || question.cardId() != cardId
        || !question.id().equals(input.clientRequestId()) || !question.expiresAt().isAfter(Instant.now()))
      throw new ApiException(409, "QUIZ_EXPIRED", "Câu hỏi đã hết hạn. Hãy mở câu hỏi mới.");
    if (question.options().stream().noneMatch(option -> option.id().equals(input.optionId())))
      throw new ApiException(400, "INVALID_REVIEW_ANSWER", "Lựa chọn không thuộc câu hỏi.");
    return notebook.addAttempt(cardId, input, Status.valueOf(card.text("status")),
        question.correctOptionId().equals(input.optionId()), fingerprint);
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

  private static String meaningKey(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFC).strip().toLowerCase(Locale.ROOT)
        .replaceAll("[.!?]+$", "").replaceAll("\\s+", " ");
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
