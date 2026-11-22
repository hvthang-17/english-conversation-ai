package vn.englishconversation.conversation.dto;

import jakarta.validation.constraints.*;

public record HintRequest(
    @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String clientRequestId) {}
