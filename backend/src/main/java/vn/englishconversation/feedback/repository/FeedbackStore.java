package vn.englishconversation.feedback.repository;

import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.conversation.repository.PracticeStore;
import vn.englishconversation.conversation.repository.PracticeStore.Row;
import vn.englishconversation.feedback.dto.*;
import vn.englishconversation.feedback.dto.FeedbackResult.*;

@Repository
@Profile("!bootstrap")
public class FeedbackStore {
  private final PracticeStore db;
  private final ObjectMapper json = new ObjectMapper();

  public FeedbackStore(PracticeStore db) {
    this.db = db;
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
INSERT INTO feedback_suggestions(feedback_id,item_type,expression,meaning_vi,example_en,sort_order)
VALUES (?,?,?,?,?,?)
""",
          id,
          suggestion.itemType(),
          suggestion.expression(),
          suggestion.meaningVi(),
          suggestion.exampleEn(),
          order++);
    }
    db.update(
        """
UPDATE session_feedback SET status='SUCCEEDED',summary=?,strengths=?,goal_assessment=?,next_practice=?,
generated_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=?
""",
        result.summary(),
        json.writeValueAsString(result.strengths()),
        result.goalAssessment(),
        result.nextPractice(),
        id);
  }

  public FeedbackResponse view(long session, int maxAttempts) {
    var optional =
        db.find(
            """
            SELECT f.*,r.error_code,r.retryable,r.attempt_count
            FROM session_feedback f JOIN ai_requests r ON r.id=f.ai_request_id WHERE f.session_id=?
            """,
            session);
    if (optional.isEmpty())
      return new FeedbackResponse("NOT_STARTED", null, false, 0, null, List.of());
    Row row = optional.get();
    String status = row.text("status");
    int attempts = (int) row.number("attempt_count");
    boolean retry = attempts < maxAttempts && ("UNKNOWN".equals(status) || row.flag("retryable"));
    if (!"SUCCEEDED".equals(status))
      return new FeedbackResponse(status, row.text("error_code"), retry, attempts, null, List.of());
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
                            item.text("example_en")))
                .toList(),
            row.text("next_practice"));
    return new FeedbackResponse(
        status,
        null,
        false,
        attempts,
        result,
        suggestions.stream().map(item -> item.text("id")).toList());
  }
}
