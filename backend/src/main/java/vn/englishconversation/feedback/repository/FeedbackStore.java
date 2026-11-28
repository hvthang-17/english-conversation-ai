package vn.englishconversation.feedback.repository;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import vn.englishconversation.learning.repository.ProgressRepository;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;
import vn.englishconversation.feedback.dto.*;
import vn.englishconversation.feedback.dto.FeedbackResult.*;
import vn.englishconversation.feedback.service.FeedbackAnalysis;
import vn.englishconversation.feedback.service.FeedbackVocabulary.Reference;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class FeedbackStore {
  private final JdbcStore db;
  private final ObjectMapper json = new ObjectMapper();

  private final Optional<ProgressRepository> progress;

  public FeedbackStore(JdbcStore db, Optional<ProgressRepository> progress) {
    this.db = db;
    this.progress = progress;
  }

  public void create(long session, long operation) {
    db.insert(
        """
        INSERT INTO session_feedback(session_id,ai_request_id,status,created_at,updated_at)
        VALUES (?,?,'PENDING',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """,
        session,
        operation);
  }

  public void setStatus(long operation, String status) {
    db.update(
        "UPDATE session_feedback SET status=?,updated_at=UTC_TIMESTAMP(6) WHERE ai_request_id=?",
        status,
        operation);
  }

  public List<Reference> vocabularyReferences(List<Suggestion> suggestions) {
    var expressions = suggestions.stream().map(Suggestion::expression).distinct().toList();
    if (expressions.isEmpty()) return List.of();
    String parameters = String.join(",", Collections.nCopies(expressions.size(), "?"));
    return db.rows("""
        SELECT i.expression,i.part_of_speech,i.meaning_vi,i.example_en
        FROM language_items i JOIN lessons l ON l.id=i.lesson_id JOIN topics t ON t.id=l.topic_id
        WHERE i.status='ACTIVE' AND l.status='ACTIVE' AND t.is_active=TRUE
          AND i.part_of_speech IS NOT NULL AND i.expression IN (%s)
          AND CHAR_LENGTH(TRIM(i.example_en)) BETWEEN 1 AND 400
          AND CHAR_LENGTH(TRIM(i.meaning_vi)) BETWEEN 1 AND 400
        ORDER BY i.id
        """.formatted(parameters), expressions.toArray()).stream()
        .map(row -> new Reference(row.text("expression"), row.text("part_of_speech"),
            row.text("meaning_vi"), row.text("example_en")))
        .toList();
  }

  public void save(long session, long operation, FeedbackResult result) {
    long id =
        db.find("SELECT id FROM session_feedback WHERE ai_request_id=?", operation)
            .orElseThrow()
            .number("id");
    int order = 0;
    for (var item : result.corrections()) {
      db.insert(
          """
INSERT INTO feedback_items(feedback_id,session_id,source_message_id,item_type,category,rule_code,
original_text,suggested_text,explanation_vi,sort_order) VALUES (?,?,?,?,?,?,?,?,?,?)
""",
          id,
          session,
          Long.parseLong(item.sourceMessageId()),
          item.itemType(),
          item.category(),
          item.ruleCode(),
          item.originalText(),
          item.suggestedText(),
          item.explanationVi(),
          order++);
    }
    order = 0;
    for (var suggestion : result.suggestions()) {
      db.insert(
          """
INSERT INTO feedback_suggestions(feedback_id,item_type,expression,meaning_vi,example_en,sort_order,part_of_speech,source_json)
VALUES (?,?,?,?,?,?,?,?)
""",
          id,
          suggestion.itemType(),
          suggestion.expression(),
          suggestion.meaningVi(),
          suggestion.exampleEn(),
          order++,
          suggestion.partOfSpeech().name(),
          suggestion.source() == null ? null : json.writeValueAsString(suggestion.source()));
    }
    db.update(
        """
UPDATE session_feedback SET status='SUCCEEDED',summary=?,strengths=?,goal_assessment=?,next_practice=?,coaching_json=?,
generated_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=?
""",
        result.summary(),
        json.writeValueAsString(result.strengths()),
        result.goalAssessment(),
        result.nextPractice(),
        result.coaching() == null ? null : json.writeValueAsString(result.coaching()),
        id);
    progress.ifPresent(inbox -> db.find("SELECT user_id FROM practice_sessions WHERE id=?",session)
        .ifPresent(owner -> inbox.notify(owner.number("user_id"),"RESULT","feedback:" + session,
            "Đã có nhận xét hội thoại","Mở lịch sử luyện tập để xem nhận xét của buổi.")));
  }

  public FeedbackResponse view(long session, int maxAttempts) {
    var optional =
        db.find(
            """
            SELECT f.*,r.error_code,r.retryable,r.attempt_count,r.prompt_version
            FROM session_feedback f JOIN ai_requests r ON r.id=f.ai_request_id WHERE f.session_id=?
            """,
            session);
    if (optional.isEmpty())
      return new FeedbackResponse("NOT_STARTED", null, false, 0, null, List.of(), List.of());
    Row row = optional.get();
    String status = row.text("status");
    int attempts = (int) row.number("attempt_count");
    boolean retry = attempts < maxAttempts && ("UNKNOWN".equals(status) || row.flag("retryable")
        || "FAILED".equals(status) && FeedbackAnalysis.canRetryRejectedV3(row.text("prompt_version"), row.text("error_code")));
    retry |= "FAILED".equals(status) && FeedbackAnalysis.canRetryAfterValidatorUpdate(
        row.text("prompt_version"), row.text("error_code"), attempts, maxAttempts);
    if (!"SUCCEEDED".equals(status))
      return new FeedbackResponse(status, row.text("error_code"), retry, attempts, null, List.of(), List.of());
    long id = row.number("id");
    var corrections =
        db.rows("SELECT * FROM feedback_items WHERE feedback_id=? ORDER BY sort_order", id).stream()
            .map(
                item ->
                    new Correction(
                        item.text("source_message_id"),
                        item.text("item_type"),
                        item.text("category"),
                        item.text("rule_code"),
                        item.text("original_text"),
                        item.text("suggested_text"),
                        item.text("explanation_vi")))
            .toList();
    var suggestions =
        db.rows("SELECT * FROM feedback_suggestions WHERE feedback_id=? ORDER BY sort_order", id);
    var result =
        new FeedbackResult(
            row.text("summary"),
            Arrays.asList(json.readValue(row.text("strengths"), String[].class)),
            row.text("goal_assessment"),
            corrections,
            suggestions.stream()
                .map(
                    item ->
                        new Suggestion(
                            item.text("item_type"),
                            item.text("expression"),
                            item.text("meaning_vi"),
                            item.text("example_en"),
                            item.text("part_of_speech") == null ? null :
                                vn.englishconversation.lesson.dto.PartOfSpeech.valueOf(item.text("part_of_speech")),
                            item.text("source_json") == null ? null :
                                json.readValue(item.text("source_json"), SuggestionSource.class)))
                .toList(),
            row.text("next_practice"),
            row.text("coaching_json") == null ? null : json.readValue(row.text("coaching_json"), Coaching.class));
    return new FeedbackResponse(
        status,
        null,
        false,
        attempts,
        result,
        suggestions.stream().map(item -> item.text("id")).toList(),
        repeatedErrors(corrections));
  }

  private static List<FeedbackResponse.RepeatedError> repeatedErrors(List<Correction> corrections) {
    return corrections.stream().filter(item -> "ERROR".equals(item.itemType()))
        .collect(java.util.stream.Collectors.groupingBy(Correction::ruleCode))
        .entrySet().stream()
        .map(group -> new FeedbackResponse.RepeatedError(group.getKey(), group.getValue().getFirst().category(),
            (int) group.getValue().stream().map(Correction::sourceMessageId).distinct().count(),
            group.getValue().getFirst()))
        .filter(group -> group.messageCount() >= 2)
        .sorted(Comparator.comparingInt(FeedbackResponse.RepeatedError::messageCount).reversed()
            .thenComparing(FeedbackResponse.RepeatedError::ruleCode))
        .limit(3).toList();
  }

  public record FeedbackOperation(long aiRequestId) {}

  public java.util.Optional<FeedbackOperation> feedbackOperation(long session) {
    return db.find("SELECT ai_request_id FROM session_feedback WHERE session_id=?", session).map(row -> new FeedbackOperation(row.number("ai_request_id")));
  }


  public record ReviewSuggestion(String itemType, String expression, String meaningVi, String exampleEn, String partOfSpeech, String meaningVerified) {}

  public Optional<ReviewSuggestion> suggestion(long user, long id) {
    return db.find(
        """
        SELECT s.item_type,s.expression,s.meaning_vi,s.example_en,s.part_of_speech,
               JSON_UNQUOTE(JSON_EXTRACT(s.source_json,'$.meaningVerified')) AS meaning_verified
        FROM feedback_suggestions s JOIN session_feedback f ON f.id=s.feedback_id
        JOIN practice_sessions p ON p.id=f.session_id
        WHERE s.id=? AND p.user_id=? AND f.status='SUCCEEDED'
        """,
        id,
        user).map(row -> new ReviewSuggestion(row.text("item_type"), row.text("expression"), row.text("meaning_vi"),
            row.text("example_en"), row.text("part_of_speech"), row.text("meaning_verified")));
  }

}
