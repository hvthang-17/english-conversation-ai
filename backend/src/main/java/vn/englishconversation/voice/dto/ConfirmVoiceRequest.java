package vn.englishconversation.voice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmVoiceRequest(@NotBlank @Size(max = 1000) String content, boolean assessPronunciation) {}
