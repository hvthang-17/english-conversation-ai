package vn.englishconversation.admin.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class AdminCatalog {
  private AdminCatalog() {}

  public record TopicInput(
      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,49}") String code,
      @NotBlank @Size(max = 150) String name,
      @Size(max = 2000) String description,
      @NotNull Boolean active,
      @NotNull @Min(0) @Max(9999) Integer sortOrder) {}

  public record Topic(String id, String code, String name, String description, boolean active, int sortOrder) {}

  public record ScenarioInput(
      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,79}") String code,
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Size(max = 2000) String description,
      @NotBlank @Size(max = 200) String learnerRole,
      @NotBlank @Size(max = 200) String aiRole,
      @NotBlank @Size(max = 1000) String communicationGoal,
      @NotBlank @Size(max = 2000) String contextDescription,
      @NotBlank @Size(max = 4000) String aiInstruction,
      @NotNull @Pattern(regexp = "DRAFT|ACTIVE|ARCHIVED") String status,
      @NotNull @Size(max = 2) List<@NotNull @Pattern(regexp = "A2|B1") String> levels) {}

  public record Scenario(String id, String code, String title, String description, String learnerRole,
      String aiRole, String communicationGoal, String contextDescription, String aiInstruction,
      String status, List<String> levels) {}
}
