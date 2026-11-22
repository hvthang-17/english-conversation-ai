package vn.englishconversation.profile.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record UpdateProfileRequest(
    @NotBlank @Size(max = 100) String displayName,
    @NotNull @Pattern(regexp = "A2|B1") String selectedLevel,
    @Size(max = 1000) String learningGoal,
    @Min(1) @Max(1440) Short dailyGoalMinutes,
    @NotNull @Size(max = 20)
        List<@NotNull @Pattern(regexp = "[1-9][0-9]{0,18}") String> topicIds) {}
