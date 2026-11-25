package vn.englishconversation.voice.dto;

public record VoiceDraft(
    String id, String status, String transcript, String expiresAt, int durationMillis,
    String errorCode, boolean retryable, boolean unknown, String retryAt) {}
