package vn.englishconversation.conversation.dto;

import jakarta.validation.constraints.*;

public record SendTextRequest(
    @NotBlank @Size(max = 1000) String content,
    @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String clientRequestId) {}
