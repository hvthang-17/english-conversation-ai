package vn.englishconversation.feedback.dto;

import java.util.List;

public record FeedbackResponse(
    String status,
    String errorCode,
    boolean retryable,
    int attempts,
    FeedbackResult result,
    List<String> suggestionIds) {}
