package vn.englishconversation.listening.service;

import static vn.englishconversation.listening.dto.ListeningData.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.common.repository.JdbcStore.Row;
import vn.englishconversation.listening.repository.ListeningRepository;
import vn.englishconversation.user.repository.UserRepository;

@Service
@Profile({"g4", "g5", "g6"})
public class ListeningAttempts {
  private static final TypeReference<List<Question>> QUESTIONS = new TypeReference<>() {};
  private static final TypeReference<List<Graded>> GRADES = new TypeReference<>() {};
  private static final TypeReference<Map<String, JsonNode>> ANSWERS = new TypeReference<>() {};
  private final ListeningRepository store;
  private final UserRepository users;
  private final ObjectMapper json;
  private final java.util.Optional<vn.englishconversation.learning.service.ProgressService> progress;

  public ListeningAttempts(ListeningRepository store, UserRepository users, ObjectMapper json,
      java.util.Optional<vn.englishconversation.learning.service.ProgressService> progress) {
    this.store = store;
    this.users = users;
    this.json = json;
    this.progress = progress;
  }

  @Transactional(readOnly = true)
  public List<Exercise> available(long lessonId) {
    return store.available(lessonId).stream().map(ListeningAttempts::exerciseSummary).toList();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ExercisePage available(int page, int size) {
    validatePage(page, size);
    return new ExercisePage(store.available(page, size).stream().map(ListeningAttempts::exerciseSummary).toList(),
        page, size, store.availableCount());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Attempt start(AccountPrincipal owner, long exerciseId, Start input) {
    lockOwner(owner);
    var replay = store.byKey(owner.id(), input.clientRequestId());
    if (replay.isPresent()) {
      if (replay.get().number("exercise_id") != exerciseId)
        throw new ApiException(409, "LISTENING_REQUEST_REUSED", "Mã yêu cầu đã được dùng cho đề khác.");
      return get(owner, replay.get().number("id"));
    }
    var existing = store.inProgress(owner.id(), exerciseId);
    if (existing.isPresent()) return get(owner, existing.get().number("id"));
    if (!store.visible(exerciseId)) throw unavailable();
    int maximum = questions(exercise(exerciseId)).size();
    return get(owner, store.begin(owner.id(), exerciseId, input.clientRequestId(), maximum));
  }

  @Transactional(readOnly = true)
  public Attempt get(AccountPrincipal owner, long id) {
    var row = owned(owner, id);
    var exercise = exercise(row.number("exercise_id"));
    var questions = questions(exercise).stream().map(Question::publicView).toList();
    boolean submitted = row.text("status").equals("SUBMITTED");
    var grades = submitted ? json.readValue(row.text("answers_json"), GRADES) : List.<Graded>of();
    Result result = submitted ? new Result((int) row.number("score"), (int) row.number("max_score"),
        exercise.text("transcript_en"), exercise.text("translation_vi"), grades) : null;
    return new Attempt(row.text("id"), exerciseSummary(exercise), row.text("status"), (int) row.number("draft_version"),
        questions, submitted ? values(grades) : json.readValue(row.text("answers_json"), ANSWERS), result, row.text("started_utc"));
  }

  @Transactional
  public Attempt save(AccountPrincipal owner, long id, Answers input) {
    var row = lockAttempt(owner, id);
    checkDraft(row, input);
    var answers = validAnswers(questions(exercise(row.number("exercise_id"))), input);
    store.draft(id, json.writeValueAsString(answers));
    return get(owner, id);
  }

  @Transactional
  public Attempt submit(AccountPrincipal owner, long id, Answers input) {
    var row = lockAttempt(owner, id);
    var questions = questions(exercise(row.number("exercise_id")));
    boolean submitted = row.text("status").equals("SUBMITTED");
    if (!submitted) checkDraft(row, input);
    Map<String, JsonNode> answers;
    try {
      answers = validAnswers(questions, input);
    } catch (ApiException error) {
      if (submitted) throw submittedConflict();
      throw error;
    }
    if (submitted) {
      if (!answers.equals(values(json.readValue(row.text("answers_json"), GRADES))))
        throw submittedConflict();
      return get(owner, id);
    }
    var grades = ListeningRules.grade(questions, answers);
    store.submit(id, grades.stream().mapToInt(Graded::pointsAwarded).sum(), json.writeValueAsString(grades));
    progress.ifPresent(service -> service.listeningSubmitted(owner.id(), id));
    return get(owner, id);
  }

  @Transactional
  public void abandon(AccountPrincipal owner, long id) {
    var row = lockAttempt(owner, id);
    if (row.text("status").equals("SUBMITTED")) throw submittedConflict();
    if (row.text("status").equals("IN_PROGRESS")) store.abandon(id);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public HistoryPage history(AccountPrincipal owner, int page, int size) {
    validatePage(page, size);
    var items = store.history(owner.id(), size, page).stream().map(row ->
        new History(row.text("id"),
            new Exercise(row.text("exercise_id"), row.text("title"), row.text("level"), row.text("instructions")),
            row.text("status"), row.text("score") == null ? null : (int) row.number("score"),
            (int) row.number("max_score"), row.text("started_utc"))).toList();
    return new HistoryPage(items, page, size, store.historyCount(owner.id()));
  }

  @Transactional(readOnly = true)
  public long audioExercise(AccountPrincipal owner, long id) {
    return owned(owner, id).number("exercise_id");
  }

  @Transactional
  public void clear(AccountPrincipal owner) {
    lockOwner(owner);
    store.clear(owner.id());
  }

  private void lockOwner(AccountPrincipal owner) {
    if (users.lockById(owner.id()).filter(account -> AccountService.valid(account, owner)).isEmpty())
      throw new ApiException(401, "SESSION_REVOKED", "Vui lòng đăng nhập lại.");
  }

  private Row lockAttempt(AccountPrincipal owner, long id) {
    lockOwner(owner);
    return store.lockAttempt(owner.id(), id).orElseThrow(ListeningAttempts::missingAttempt);
  }

  private Row owned(AccountPrincipal owner, long id) {
    return store.attempt(owner.id(), id).orElseThrow(ListeningAttempts::missingAttempt);
  }

  private Row exercise(long id) {
    return store.exercise(id).orElseThrow(ListeningAttempts::unavailable);
  }

  private List<Question> questions(Row exercise) {
    return json.readValue(exercise.text("questions_json"), QUESTIONS);
  }

  private static Map<String, JsonNode> values(List<Graded> grades) {
    var answers = new LinkedHashMap<String, JsonNode>();
    for (var grade : grades) if (grade.answerValue() != null && !grade.answerValue().isNull())
      answers.put(grade.questionCode(), grade.answerValue());
    return answers;
  }

  private static Map<String, JsonNode> validAnswers(List<Question> questions, Answers input) {
    try {
      return ListeningRules.answers(questions, input.answers());
    } catch (IllegalArgumentException invalid) {
      throw new ApiException(400, "INVALID_LISTENING_ANSWER", "Câu trả lời không hợp lệ cho đề này.");
    }
  }

  private static void checkDraft(Row row, Answers input) {
    if (!row.text("status").equals("IN_PROGRESS"))
      throw new ApiException(409, "LISTENING_ATTEMPT_CLOSED", "Lượt làm đã kết thúc.");
    if (row.number("draft_version") != input.draftVersion())
      throw new ApiException(409, "LISTENING_DRAFT_CHANGED", "Bản nháp đã thay đổi. Hãy tải lại bản đã lưu.");
  }
  private static Exercise exerciseSummary(Row row) {
    return new Exercise(row.text("id"), row.text("title"), row.text("level"), row.text("instructions"));
  }
  private static void validatePage(int page, int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 50)
      throw new ApiException(400, "INVALID_PAGINATION", "Trang hoặc kích thước trang không hợp lệ.");
  }
  private static ApiException missingAttempt() {
    return new ApiException(404, "LISTENING_ATTEMPT_NOT_FOUND", "Không tìm thấy lượt luyện nghe.");
  }
  private static ApiException unavailable() {
    return new ApiException(404, "LISTENING_EXERCISE_NOT_FOUND", "Đề luyện nghe không còn khả dụng.");
  }
  private static ApiException submittedConflict() {
    return new ApiException(409, "LISTENING_ALREADY_SUBMITTED", "Lượt này đã nộp với đáp án khác hoặc đã khóa.");
  }
}
