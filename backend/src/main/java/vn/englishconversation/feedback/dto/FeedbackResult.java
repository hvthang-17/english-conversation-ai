package vn.englishconversation.feedback.dto;

import java.util.List;

public record FeedbackResult(
    String summary,
    List<String> strengths,
    String goalAssessment,
    List<Correction> corrections,
    List<Suggestion> suggestions,
    String nextPractice) {
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
      vn.englishconversation.lesson.dto.PartOfSpeech partOfSpeech) {}
}
