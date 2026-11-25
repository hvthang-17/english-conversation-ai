package vn.englishconversation.conversation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ExplanationRequest(
    @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String clientRequestId,
    boolean retry,
    boolean acknowledgeUnknown) {}
