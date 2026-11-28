package vn.englishconversation.conversation.dto;

public record HintResponse(String messageId, String operationId, String status, String hint,
    String errorCode, boolean retryable, String retryNotBefore) {}
