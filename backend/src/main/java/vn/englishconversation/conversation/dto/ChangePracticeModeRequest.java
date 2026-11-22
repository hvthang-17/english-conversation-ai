package vn.englishconversation.conversation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ChangePracticeModeRequest(@NotNull @Pattern(regexp = "TEXT") String mode) {}
