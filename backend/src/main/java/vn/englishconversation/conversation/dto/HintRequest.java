package vn.englishconversation.conversation.dto;

import jakarta.validation.constraints.*;

public record HintRequest(
    @NotNull @Pattern(regexp = "[1-9][0-9]{0,18}") String messageId,
    @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String clientRequestId) {}
