package vn.englishconversation.conversation.dto;

import jakarta.validation.constraints.*;

public record CreatePracticeRequest(
    @NotNull @Pattern(regexp = "[1-9][0-9]{0,18}") String scenarioId,
    @NotNull @Pattern(regexp = "A2|B1") String level,
    @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String clientRequestId,
    @Pattern(regexp = "TEXT|VOICE") String mode,
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}") String roleCode) {
  public CreatePracticeRequest(String scenarioId, String level, String clientRequestId) {
    this(scenarioId, level, clientRequestId, "TEXT", "DEFAULT");
  }

  public CreatePracticeRequest(String scenarioId, String level, String clientRequestId, String mode) {
    this(scenarioId, level, clientRequestId, mode, "DEFAULT");
  }

  public CreatePracticeRequest {
    if (mode == null) mode = "TEXT";
    if (roleCode == null) roleCode = "DEFAULT";
  }
}
