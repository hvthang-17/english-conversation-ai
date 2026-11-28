package vn.englishconversation.feedback.dto;

import java.util.List;

public record FeedbackResult(
    String summary,
    List<String> strengths,
    String goalAssessment,
    List<Correction> corrections,
    List<Suggestion> suggestions,
    String nextPractice,
    Coaching coaching) {
  public record Correction(
      String sourceMessageId,
      String itemType,
      String category,
      String ruleCode,
      String originalText,
      String suggestedText,
      String explanationVi) {}

  public record Suggestion(
      String itemType, String expression, String meaningVi, String exampleEn,
      vn.englishconversation.lesson.dto.PartOfSpeech partOfSpeech, SuggestionSource source) {}

  public record SuggestionSource(
      String kind, String messageId, String excerpt, String observedForm, String useVi, Boolean meaningVerified) {}
  public record Coaching(int analyzedTurns, int totalTurns, List<TurnReview> turns,
      List<GoalReview> goals, Replay replay) {}
  public record TurnReview(String learnerMessageId, String assistantMessageId, String status,
      String questionQuality, String explanationVi, String adviceKind, String suggestionEn) {}
  public record GoalReview(String labelVi, String status, List<String> learnerMessageIds) {}
  public record Replay(String learnerMessageId, String criterionVi, String transferQuestionEn) {}
}
