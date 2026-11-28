package vn.englishconversation.feedback;


import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.conversation.dto.*;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class FeedbackIntegrationMysqlTest extends FullSystemMysqlFixture {

  @Test
  void aggregatesOnlyRepeatedErrorsAcrossDifferentMessages(
      @Autowired vn.englishconversation.learning.repository.ProgressRepository progress) {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    service.send(owner, id, new SendTextRequest("I want apple, I want orange.", "repeat-error-first"));
    service.send(owner, id, new SendTextRequest("I want egg.", "repeat-error-second"));
    var latest = service.send(owner, id, new SendTextRequest("I want a drink.", "repeat-error-third"));
    String first = latest.messages().get(1).id(), second = latest.messages().get(3).id();
    String third = latest.messages().get(5).id();
    String correction = """
        {"sourceMessageId":"%s","itemType":"%s","category":"GRAMMAR","ruleCode":"ARTICLE",
         "originalText":"%s","suggestedText":"%s","explanationVi":"Thêm mạo từ."}
        """;
    provider.feedbackOutput = CLEAN_REVIEW.replace("\"corrections\":[]", "\"corrections\":[" +
        correction.formatted(first, "ERROR", "want apple", "want an apple") + "," +
        correction.formatted(first, "ERROR", "want orange", "want an orange") + "," +
        correction.formatted(second, "ERROR", "want egg", "want an egg") + "," +
        correction.formatted(third, "IMPROVEMENT", "I want a drink", "May I have a drink") + "]");
    service.finish(owner, id);
    var result = service.generateFeedback(owner, id, false, false);
    assertEquals("SUCCEEDED", result.status());
    assertEquals(1, result.repeatedErrors().size());
    var group = result.repeatedErrors().getFirst();
    assertEquals("ARTICLE", group.ruleCode());
    assertEquals(2, group.messageCount());
    assertEquals(2, learning.statistics(owner).errorGroups().getFirst().count());
    var dashboardGroups = progress.repeatedErrors(owner.id());
    assertEquals(1, dashboardGroups.size());
    assertEquals(2, dashboardGroups.getFirst().sentences());
    assertEquals(1, jdbc.queryForObject("""
        SELECT COUNT(*) FROM notifications WHERE user_id=? AND dedupe_key=?
        """, Integer.class, owner.id(), "feedback:" + id));
    assertEquals(first, group.example().sourceMessageId());
    int count = provider.calls.get();
    assertEquals(result, service.getFeedback(owner, id));
    assertEquals(count, provider.calls.get());
  }

  @Test
  void feedbackShowsSchoolAnswerFrameForNewAndSavedReviews() {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    var sent = service.send(owner, id, new SendTextRequest("I usually go to school at 7", "school-answer"));
    jdbc.update("UPDATE messages SET content=? WHERE id=?",
        "My classes finish at 11:30. What time do you finish school?", sent.messages().getFirst().id());
    service.finish(owner, id);
    provider.feedbackOutput = CLEAN_REVIEW;
    try {
      var result = service.generateFeedback(owner, id, false, false);
      assertEquals("SUCCEEDED", result.status());
      var turn = result.result().coaching().turns().getFirst();
      assertEquals("OFF_TOPIC", turn.status());
      assertEquals("I finish school at [time].", turn.suggestionEn());
      jdbc.update("""
          UPDATE session_feedback SET coaching_json=JSON_SET(coaching_json,
            '$.turns[0].status', 'PARTIAL', '$.turns[0].adviceKind', 'NONE', '$.turns[0].suggestionEn', NULL)
          WHERE session_id=?
          """, id);
      int calls = provider.calls.get();
      var savedTurn = service.getFeedback(owner, id).result().coaching().turns().getFirst();
      assertEquals(turn, savedTurn);
      assertEquals(calls, provider.calls.get());
    } finally {
      provider.feedbackOutput = null;
    }
  }

  @Test
  void rejectedFeedbackV3CanRetryOnceWithTheUpdatedPrompt() {
    var owner = owner();
    long session = Long.parseLong(service.create(owner, draft()).id());
    service.send(owner, session, new SendTextRequest("Soup, please.", "rejected-v3-turn"));
    service.finish(owner, session);
    jdbc.update("""
        INSERT INTO ai_requests(user_id,session_id,task_type,operation_key,request_fingerprint,
          status,prompt_version,attempt_count,error_code,created_at)
        VALUES (?,?,'FEEDBACK','rejected-feedback-fixture',REPEAT('a',64),'FAILED',
          'practice-feedback-v3',1,'PROVIDER_REQUEST_REJECTED',UTC_TIMESTAMP(6))
        """, owner.id(), session);
    long operation = jdbc.queryForObject("SELECT id FROM ai_requests WHERE session_id=? AND task_type='FEEDBACK'",
        Long.class, session);
    jdbc.update("""
        INSERT INTO session_feedback(session_id,ai_request_id,status,created_at,updated_at)
        VALUES (?,?,'FAILED',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """, session, operation);
    try {
      assertTrue(service.getFeedback(owner, session).retryable());
      provider.feedbackOutput = CLEAN_REVIEW;
      var recovered = service.generateFeedback(owner, session, true, false);
      assertEquals("SUCCEEDED", recovered.status());
      assertEquals(2, recovered.attempts());
      assertEquals("practice-feedback-v3.4",
          jdbc.queryForObject("SELECT prompt_version FROM ai_requests WHERE id=?", String.class, operation));
    } finally {
      provider.feedbackOutput = null;
    }
  }

  @Test
  void feedbackV31CanRecoverFromValidationFailure() {
    var owner = owner();
    long session = Long.parseLong(service.create(owner, draft()).id());
    service.send(owner, session, new SendTextRequest("Soup, please.", "invalid-v31-turn"));
    service.finish(owner, session);
    jdbc.update("""
        INSERT INTO ai_requests(user_id,session_id,task_type,operation_key,request_fingerprint,
          status,prompt_version,attempt_count,error_code,created_at)
        VALUES (?,?,'FEEDBACK','invalid-v31-fixture',REPEAT('a',64),'FAILED',
          'practice-feedback-v3.1',2,'FEEDBACK_INVALID',UTC_TIMESTAMP(6))
        """, owner.id(), session);
    jdbc.update("""
        INSERT INTO session_feedback(session_id,ai_request_id,status,created_at,updated_at)
        SELECT session_id,id,'FAILED',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
          FROM ai_requests WHERE session_id=? AND task_type='FEEDBACK'
        """, session);
    try {
      assertTrue(service.getFeedback(owner, session).retryable());
      provider.feedbackOutput = CLEAN_REVIEW;
      var recovered = service.generateFeedback(owner, session, true, false);
      assertEquals("SUCCEEDED", recovered.status());
      assertEquals(3, recovered.attempts());
      assertEquals(recovered.result(), service.getFeedback(owner, session).result());
    } finally {
      provider.feedbackOutput = null;
    }
  }

  @Test
  void feedbackV3PersistsCoachingAndVocabularyEvidence() {
    var owner = owner();
    long session = Long.parseLong(service.create(owner, draft()).id());
    var exchange = service.send(owner, session, new SendTextRequest("Yes.", "feedback-v3-input"));
    String assistant = exchange.messages().getFirst().id();
    String learner = exchange.messages().get(1).id();
    service.finish(owner, session);
    provider.feedbackOutput = """
        {"summary":"Bạn còn thiếu ý.","strengths":[],"goalAssessment":"Chưa gọi món cụ thể.",
         "nextPractice":"Nêu món muốn gọi.","corrections":[],"turns":[
          {"learnerMessageId":"%s","assistantMessageId":"%s","status":"PARTIAL","questionQuality":"CLEAR",
           "explanationVi":"Hãy nêu món bạn muốn.","adviceKind":"FILL_IN","suggestionEn":"Yes, I would like [your dish]."}],
         "goals":[{"labelVi":"Gọi món","status":"PARTIAL","learnerMessageIds":["%s"]}],
         "replay":{"learnerMessageId":"%s","criterionVi":"Nêu một món.","transferQuestionEn":"Which dish would you like?"},
         "suggestions":[{"itemType":"PHRASE","expression":"would you like","meaningVi":"bạn có muốn không",
          "exampleEn":"Would you like soup?","partOfSpeech":"EXPRESSION","sourceKind":"IN_TRANSCRIPT",
          "sourceMessageId":"%s","sourceExcerpt":"What would you like to order?","observedForm":"would you like",
          "useVi":"Dùng khi hỏi người khác muốn gọi gì."}]}
        """.formatted(learner, assistant, learner, learner, assistant);
    try {
      var result = service.generateFeedback(owner, session, false, false);
      assertEquals("SUCCEEDED", result.status());
      assertEquals(learner, result.result().coaching().replay().learnerMessageId());
      assertEquals(assistant, result.result().suggestions().getFirst().source().messageId());
      assertEquals(result.result(), service.getFeedback(owner, session).result());
      assertEquals(1, result.suggestionIds().size());
      assertEquals(Boolean.FALSE, result.result().suggestions().getFirst().source().meaningVerified());
      var unavailable = assertThrows(ApiException.class,
          () -> learning.save(owner, Long.parseLong(result.suggestionIds().getFirst())));
      assertEquals(409, unavailable.status);
    } finally {
      provider.feedbackOutput = null;
    }
  }

  @Test
  void feedbackOmitsWrongVocabularyMeaningAndKeepsReviewedCardSaveable() {
    var owner = owner();
    long session = Long.parseLong(service.create(owner, draft()).id());
    service.send(owner, session, new SendTextRequest("Soup, please.", "feedback-vocabulary"));
    service.finish(owner, session);
    provider.feedbackOutput = CLEAN_REVIEW.replace(
        "\"suggestions\":[", """
        "suggestions":[{"itemType":"VOCABULARY","expression":"receipt","meaningVi":"phòng ngủ",
        "exampleEn":"Would you like a receipt?","partOfSpeech":"NOUN"},
        """);
    try {
      var result = service.generateFeedback(owner, session, false, false);
      assertEquals("SUCCEEDED", result.status());
      assertEquals(1, result.suggestionIds().size());
      var suggestion = result.result().suggestions().getFirst();
      assertEquals("Hóa đơn mua hàng", suggestion.meaningVi());
      assertEquals("I brought the receipt with me.", suggestion.exampleEn());
      assertEquals(Boolean.TRUE, suggestion.source().meaningVerified());
      assertEquals(suggestion.meaningVi(), learning.save(owner,
          Long.parseLong(result.suggestionIds().getFirst())).meaningVi());
    } finally {
      provider.feedbackOutput = null;
    }
  }

  @Test
  void feedbackRejectsAssistantSourceAndRetriesOnlyFailedResult() {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    assertEquals(
        409,
        assertThrows(ApiException.class, () -> service.generateFeedback(owner, id, false, false))
            .status);
    var sent = service.send(owner, id, new SendTextRequest("I want order soup.", "feedback-wrong"));
    var learner = sent.messages().get(1);
    var opening = sent.messages().getFirst();
    service.finish(owner, id);
    assertEquals(
        404, assertThrows(ApiException.class, () -> service.getFeedback(owner(), id)).status);
    String correction =
        """
        [{"sourceMessageId":"%s","itemType":"ERROR","category":"GRAMMAR","ruleCode":"VERB_FORM",
        "originalText":"want order","suggestedText":"want to order",
        "explanationVi":"Sau want, dùng to + động từ nguyên mẫu để nói điều bạn muốn làm."}]
        """;
    provider.feedbackOutput =
        CLEAN_REVIEW.replace(
            "\"corrections\":[]", "\"corrections\":" + correction.formatted(opening.id()));
    var failed = service.generateFeedback(owner, id, false, false);
    assertEquals("FEEDBACK_INVALID", failed.errorCode());
    assertEquals("FAILED", failed.status());
    assertNull(failed.result());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM feedback_items WHERE session_id=?", Integer.class, id));
    assertEquals("COMPLETED", service.get(owner, id).status());
    assertEquals(sent.messages(), service.get(owner, id).messages());

    provider.feedbackOutput =
        CLEAN_REVIEW.replace(
            "\"corrections\":[]", "\"corrections\":" + correction.formatted(learner.id()));
    var result = service.generateFeedback(owner, id, true, false);
    assertEquals("SUCCEEDED", result.status());
    assertEquals(2, result.attempts());
    assertEquals(learner.id(), result.result().corrections().getFirst().sourceMessageId());
    assertEquals("want to order", result.result().corrections().getFirst().suggestedText());
    assertEquals(1, result.suggestionIds().size());
    int calls = provider.calls.get();
    assertEquals(result, service.generateFeedback(owner, id, false, false));
    assertEquals(result, service.generateFeedback(owner, id, true, false));
    assertEquals(calls, provider.calls.get());
  }

  @Test
  void feedbackAllowsEmptyCorrectionsAndRejectsInvalidSourceCategoryAndShape() {
    var owner = owner();
    long id = Long.parseLong(service.create(owner, draft()).id());
    var sent =
        service.send(
            owner, id, new SendTextRequest("I would like soup, please.", "feedback-clean"));
    service.finish(owner, id);
    var analysis = new vn.englishconversation.feedback.service.FeedbackAnalysis();
    assertThrows(
        IllegalArgumentException.class,
        () -> analysis.validate(CLEAN_REVIEW + "{}", sent.messages()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            analysis.validate(
                CLEAN_REVIEW.replace(
                    "\"strengths\":[\"Dùng please để gọi món lịch sự.\"]", "\"strengths\":false"),
                sent.messages()));
    String item =
        """
        [{"sourceMessageId":"%s","itemType":"%s","category":"%s","ruleCode":"%s",
        "originalText":"%s","suggestedText":"different text","explanationVi":"Giải thích."}]
        """;
    String source = sent.messages().get(1).id();
    long otherSession = Long.parseLong(service.create(owner, draft()).id());
    String foreignSource = service.send(owner, otherSession,
        new SendTextRequest("I would like soup, please.", "foreign-feedback-source"))
        .messages().get(1).id();
    for (String invalid :
        java.util.List.of(
            item.formatted(foreignSource, "ERROR", "GRAMMAR", "ARTICLE", "soup"),
            item.formatted(source, "ERROR", "GRAMMAR", "ARTICLE", "invented fragment"),
            item.formatted(source, "ERROR", "EXPRESSION", "POLITENESS", "soup"),
            item.formatted(source, "IMPROVEMENT", "GRAMMAR", "WORD_CHOICE", "soup"))) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              analysis.validate(
                  CLEAN_REVIEW.replace("\"corrections\":[]", "\"corrections\":" + invalid),
                  sent.messages()));
    }
    provider.feedbackOutput = CLEAN_REVIEW;
    var result = service.generateFeedback(owner, id, false, false);
    assertEquals("SUCCEEDED", result.status());
    assertTrue(result.result().corrections().isEmpty());
    assertEquals(result, service.getFeedback(owner, id));
  }
}
