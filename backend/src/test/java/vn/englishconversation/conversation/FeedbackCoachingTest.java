package vn.englishconversation.conversation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.englishconversation.conversation.dto.MessageResponse;
import vn.englishconversation.feedback.dto.FeedbackResult.Suggestion;
import vn.englishconversation.feedback.dto.FeedbackResult.SuggestionSource;
import vn.englishconversation.feedback.service.FeedbackAnalysis;
import vn.englishconversation.feedback.service.FeedbackVocabulary;
import vn.englishconversation.lesson.dto.PartOfSpeech;

class FeedbackCoachingTest {
  private final FeedbackAnalysis analysis = new FeedbackAnalysis();
  private final List<MessageResponse> transcript = List.of(
      message("1", "ASSISTANT", "What time would you like to check in? We have a quiet room."),
      message("2", "LEARNER", "7"),
      message("3", "ASSISTANT", "Would you like a receipt?"),
      message("4", "LEARNER", "No."));
  private final String response = """
      {"summary":"Bạn đã trả lời hai câu.","strengths":["Từ chối biên lai rõ ràng."],
       "goalAssessment":"Cần làm rõ giờ nhận phòng.","nextPractice":"Xác nhận buổi sáng hay tối.",
       "corrections":[],"suggestions":[
        {"itemType":"PHRASE","expression":"a quiet room","meaningVi":"phòng yên tĩnh",
         "exampleEn":"Could I book a quiet room?","partOfSpeech":"NOUN_PHRASE","sourceKind":"IN_TRANSCRIPT",
         "sourceMessageId":"1","sourceExcerpt":"We have a quiet room.","observedForm":"a quiet room","useVi":"Hỏi về phòng."},
        {"itemType":"PHRASE","expression":"late check-out","meaningVi":"trả phòng muộn",
         "exampleEn":"Is late check-out available?","partOfSpeech":"NOUN_PHRASE","sourceKind":"TOPIC_EXTENSION",
         "sourceMessageId":null,"sourceExcerpt":null,"observedForm":null,"useVi":"Hỏi về giờ trả phòng."}],
       "turns":[
        {"learnerMessageId":"2","assistantMessageId":"1","status":"PARTIAL","questionQuality":"CLEAR",
         "explanationVi":"Cần nêu rõ sáng hay tối.","adviceKind":"FILL_IN","suggestionEn":"I'd like to check in at seven [a.m./p.m.]."},
        {"learnerMessageId":"4","assistantMessageId":"3","status":"COMPLETE","questionQuality":"CLEAR",
         "explanationVi":"Bạn đã từ chối biên lai.","adviceKind":"PRESERVE_MEANING","suggestionEn":"No, thank you."}],
       "goals":[{"labelVi":"Xác nhận giờ nhận phòng","status":"PARTIAL","learnerMessageIds":["2"]}],
       "replay":{"learnerMessageId":"2","criterionVi":"Nêu giờ và buổi trong ngày.",
         "transferQuestionEn":"When would you like to check out?"}}
      """;

  @Test
  void acceptsShortAnswersWithoutMakingThemLanguageErrors() {
    var result = analysis.validate(response, transcript, prompt());
    assertTrue(result.corrections().isEmpty());
    assertEquals("COMPLETE", result.coaching().turns().getLast().status());
    assertEquals("FILL_IN", result.coaching().turns().getFirst().adviceKind());
    assertEquals("2", result.coaching().replay().learnerMessageId());
    assertEquals(List.of("IN_TRANSCRIPT", "TOPIC_EXTENSION"), result.suggestions().stream().map(s -> s.source().kind()).toList());
  }

  @Test
  void suggestsTheRequestedSchoolTimeInsteadOfReusingArrivalTime() {
    var schoolMessages = List.of(
        message("1", "ASSISTANT", "My classes finish at 11:30. What time do you finish school?"),
        message("2", "LEARNER", "I usually go to school at 7"),
        transcript.get(2), transcript.get(3));
    var schoolPrompt = analysis.prompt("A2", "Trường học", "Hỏi giờ tan học", "Student", "Classmate",
        "School routine", "TEXT", schoolMessages, 80000);
    var turn = analysis.validate(response, schoolMessages, schoolPrompt).coaching().turns().getFirst();
    assertEquals("OFF_TOPIC", turn.status());
    assertEquals("FILL_IN", turn.adviceKind());
    assertEquals("I finish school at [time].", turn.suggestionEn());
  }

  @Test
  void suppliesAnAnswerFrameWhenTheClearQuestionWasOnlyPartlyAnswered() {
    var scheduling = List.of(message("1", "ASSISTANT", "Which day and time work for you?"),
        message("2", "LEARNER", "Friday."), transcript.get(2), transcript.get(3));
    var schedulePrompt = analysis.prompt("B1", "Học nhóm", "Chọn lịch", "Student", "Classmate",
        "Study group", "TEXT", scheduling, 80000);
    String missing = response.replace("\"adviceKind\":\"FILL_IN\",\"suggestionEn\":\"I'd like to check in at seven [a.m./p.m.].\"",
        "\"adviceKind\":\"NONE\",\"suggestionEn\":null");
    assertEquals("Friday at [time] works for me.",
        analysis.validate(missing, scheduling, schedulePrompt).coaching().turns().getFirst().suggestionEn());
    var ambiguous = missing.replaceFirst("\"status\":\"PARTIAL\",\"questionQuality\":\"CLEAR\"",
        "\"status\":\"UNCERTAIN\",\"questionQuality\":\"AMBIGUOUS\"");
    assertNull(analysis.validate(ambiguous, scheduling, schedulePrompt).coaching().turns().getFirst().suggestionEn());
  }

  @Test
  void dropsRewritesThatChangeNumbersOrPolarityAndRejectsWrongSources() {
    var unsafe = response.replace("at seven", "at eight").replace("No, thank you.", "Yes, please.");
    var result = analysis.validate(unsafe, transcript, prompt());
    assertTrue(result.coaching().turns().stream().allMatch(turn -> turn.suggestionEn() == null));
    assertThrows(IllegalArgumentException.class, () -> analysis.validate(
        response.replace("\"assistantMessageId\":\"1\"", "\"assistantMessageId\":\"3\""), transcript, prompt()));
    var discarded = analysis.validate(response.replace("\"sourceMessageId\":\"1\"", "\"sourceMessageId\":\"4\"")
        .replace("\"assistantMessageId\":\"1\"", "\"assistantMessageId\":1")
        .replace("\"replay\":{\"learnerMessageId\":\"2\"", "\"replay\":{\"learnerMessageId\":\"999\""), transcript, prompt());
    assertEquals(2, discarded.coaching().analyzedTurns());
    assertNull(discarded.coaching().replay());
    assertEquals(List.of("late check-out"), discarded.suggestions().stream().map(s -> s.expression()).toList());
    assertThrows(IllegalArgumentException.class, () -> analysis.validate(
        response.replace("\"questionQuality\":\"CLEAR\"", "\"questionQuality\":\"AMBIGUOUS\""), transcript, prompt()));
  }

  @Test
  void preservesFinalCorrectedTimeAndProtectsOrderedAndReceivedColors() {
    String advice = "I'd like to check in at seven [a.m./p.m.].";
    String timed = response.replace(advice, "I'll arrive at seven.")
        .replace("\"FILL_IN\"", "\"PRESERVE_MEANING\"");
    var timeMessages = List.of(transcript.getFirst(), message("2", "LEARNER", "At six, sorry, at seven."),
        transcript.get(2), transcript.get(3));
    var timePrompt = analysis.prompt("B1", "Đặt phòng", "Xác nhận giờ", "Guest", "Receptionist", "Hotel",
        "VOICE", timeMessages, 80000);
    assertEquals("I'll arrive at seven.",
        analysis.validate(timed, timeMessages, timePrompt).coaching().turns().getFirst().suggestionEn());
    assertNull(analysis.validate(timed.replace("at seven.", "at six."), timeMessages, timePrompt)
        .coaching().turns().getFirst().suggestionEn());

    String original = "I ordered a blue shirt but got a red one.";
    String reversed = "I ordered a red shirt but got a blue one.";
    var colorMessages = List.of(message("1", "ASSISTANT", "Why do you need a refund?"),
        message("2", "LEARNER", original), transcript.get(2), transcript.get(3));
    var colorPrompt = analysis.prompt("B1", "Đổi hàng", "Nêu lý do", "Customer", "Clerk", "Shop",
        "TEXT", colorMessages, 80000);
    var colorResponse = timed.replace("I'll arrive at seven.", reversed).replace("\"corrections\":[]",
        """
        "corrections":[{"sourceMessageId":"2","itemType":"IMPROVEMENT","category":"EXPRESSION",
        "ruleCode":"NATURALNESS","originalText":"%s","suggestedText":"%s","explanationVi":"Cách nói khác."}]
        """.formatted(original, reversed));
    var result = analysis.validate(colorResponse, colorMessages, colorPrompt);
    assertNull(result.coaching().turns().getFirst().suggestionEn());
    assertTrue(result.corrections().isEmpty());
    assertEquals(2, result.coaching().analyzedTurns());
    String faithful = "I received a red shirt, but I ordered a blue one.";
    assertEquals(faithful, analysis.validate(colorResponse.replace(reversed, faithful), colorMessages, colorPrompt)
        .coaching().turns().getFirst().suggestionEn());
  }

  @Test
  void verifiesBookingSenseAndLeavesUnmatchedSuggestionsUnavailableForCards() {
    var initial = analysis.validate(response, transcript, prompt());
    var known = new Suggestion("VOCABULARY", "book", "đặt phòng", "I booked a quiet room.", PartOfSpeech.VERB,
        new SuggestionSource("IN_TRANSCRIPT", "1", "Could I book a room?", "book", "Dùng khi đặt phòng.", null));
    var otherSense = new Suggestion("VOCABULARY", "book", "đặt phòng", "I booked a quiet room.", PartOfSpeech.VERB,
        new SuggestionSource("IN_TRANSCRIPT", "1", "I read a book in a room.", "book", "Dùng khi đặt phòng.", null));
    var result = new vn.englishconversation.feedback.dto.FeedbackResult(initial.summary(), initial.strengths(),
        initial.goalAssessment(), initial.corrections(), List.of(known, otherSense),
        initial.nextPractice(), initial.coaching());
    var suggestions = FeedbackVocabulary.verify(result, List.of()).suggestions();
    assertEquals(Boolean.TRUE, suggestions.getFirst().source().meaningVerified());
    assertEquals("đặt trước", suggestions.getFirst().meaningVi());
    assertEquals(Boolean.FALSE, suggestions.get(1).source().meaningVerified());
  }

  @Test
  void sendsFeedbackAsJsonWithTheDetailedContractInTheInstruction() {
    var provider = new vn.englishconversation.ai.provider.GeminiTextProvider(
        new vn.englishconversation.ai.config.ChatSettings(new org.springframework.core.env.StandardEnvironment()),
        java.net.http.HttpClient.newHttpClient(), java.net.URI.create("http://127.0.0.1"));
    var body = new tools.jackson.databind.ObjectMapper().readTree(provider.payload(prompt()));
    assertEquals("application/json", body.path("generationConfig").path("responseMimeType").asString());
    assertFalse(body.path("generationConfig").has("responseJsonSchema"));
    assertTrue(body.path("systemInstruction").path("parts").path(0).path("text").asString().contains("learnerMessageId"));
    var structured = new vn.englishconversation.ai.provider.TextProvider.Prompt("Fixture", List.of(), 128,
        java.util.Map.of("type", "object"));
    assertTrue(new tools.jackson.databind.ObjectMapper().readTree(provider.payload(structured))
        .path("generationConfig").has("responseJsonSchema"));
  }

  @Test
  void reportsCoverageWhenOnlyTheFirstLearnerTurnFits() {
    var first = analysis.prompt("A2", "Đặt phòng", "Xác nhận giờ", "Guest", "Receptionist", "Hotel",
        "VOICE", transcript.subList(0, 2), 80000);
    int budget = first.instruction().length() + first.history().getFirst().content().length();
    var partialPrompt = analysis.prompt("A2", "Đặt phòng", "Xác nhận giờ", "Guest", "Receptionist",
        "Hotel", "VOICE", transcript, budget);
    var json = new tools.jackson.databind.ObjectMapper();
    var partial = json.readTree(response);
    ((tools.jackson.databind.node.ArrayNode) partial.get("turns")).remove(1);
    var result = analysis.validate(json.writeValueAsString(partial), transcript, partialPrompt);
    assertEquals(1, result.coaching().analyzedTurns());
    assertEquals(2, result.coaching().totalTurns());
    assertThrows(IllegalArgumentException.class, () -> analysis.validate(response, transcript, partialPrompt));
  }

  private vn.englishconversation.ai.provider.TextProvider.Prompt prompt() {
    return analysis.prompt("A2", "Đặt phòng", "Xác nhận giờ", "Guest", "Receptionist", "Hotel", "VOICE", transcript, 80000);
  }

  private static MessageResponse message(String id, String sender, String content) {
    return new MessageResponse(id, id, Integer.parseInt(id), sender, content, "", null);
  }
}
