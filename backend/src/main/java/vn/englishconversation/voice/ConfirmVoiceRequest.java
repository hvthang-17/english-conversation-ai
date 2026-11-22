package vn.englishconversation.voice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmVoiceRequest(@NotBlank @Size(max = 1000) String content) {}
