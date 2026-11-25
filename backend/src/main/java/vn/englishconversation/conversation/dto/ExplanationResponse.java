package vn.englishconversation.conversation.dto;

public record ExplanationResponse(
    String messageId, String status, String errorCode, boolean retryable,
    String retryNotBefore, String simpleEnglish, String meaningVi) {}
