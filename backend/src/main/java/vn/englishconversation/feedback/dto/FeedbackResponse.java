package vn.englishconversation.feedback.dto;

import java.util.List;

public record FeedbackResponse(
    String status,
    String errorCode,
    boolean retryable,
    int attempts,
    FeedbackResult result,
    List<String> suggestionIds,
    List<RepeatedError> repeatedErrors) {
  public record RepeatedError(String ruleCode, String category, int messageCount,
      FeedbackResult.Correction example) {}
}
