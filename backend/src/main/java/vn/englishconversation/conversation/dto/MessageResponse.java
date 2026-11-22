package vn.englishconversation.conversation.dto;

public record MessageResponse(
    String id, String turnId, int turnNo, String sender, String content, String createdAt) {}
