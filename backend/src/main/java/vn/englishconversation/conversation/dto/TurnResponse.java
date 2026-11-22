package vn.englishconversation.conversation.dto;

public record TurnResponse(
    String id, int turnNo, String clientRequestId, String kind, String status, String requestId) {}
