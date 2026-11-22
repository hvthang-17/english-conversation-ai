package vn.englishconversation.conversation.dto;

public record OperationResponse(
    String id,
    String task,
    String status,
    String errorCode,
    boolean retryable,
    int attempts,
    String retryNotBefore) {}
