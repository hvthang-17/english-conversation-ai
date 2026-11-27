package vn.englishconversation.learning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import vn.englishconversation.learning.dto.LearningRecommendation.Focus;

public final class LearningPlanData {
  private LearningPlanData() {}

  public enum SourceType { LESSON, REVIEW_ITEM }
  public enum Result { COMPLETED, SKIPPED }

  public record Settings(@NotBlank @Size(max=1000) String goal, Focus focusSkill,
      @NotNull LocalDate startDate, @NotNull @Min(1) @Max(7) Integer days) {}
  public record Create(@NotNull @Valid Settings settings, @Positive Long replaceId, @Positive Long version) {}
  public record Edit(@NotNull @Valid Settings settings, @NotNull @Positive Long version) {}
  public record Source(@NotNull SourceType type, @NotNull @Positive Long id) {}
  public record AddItem(@NotNull @Positive Long version, @NotNull LocalDate dueDate,
      @NotNull @Positive Integer position, @NotNull @Valid Source source, Boolean recommended) {}
  public record EditItem(@NotNull @Positive Long version, @NotNull LocalDate dueDate,
      @NotNull @Positive Integer position, @Valid Source source) {}
  public record FinishItem(@NotNull @Positive Long version, @NotNull Result result) {}
  public record Summary(String id, String goal, String focusSkill, LocalDate startDate, LocalDate endDate,
      String timeZone, String status, long version) {}
  public record Item(String id, LocalDate dueDate, int position, String lessonId, String reviewItemId,
      String title, boolean available, String reasonCode, String status, String completedAt) {}
  public record Detail(Summary plan, List<Item> items) {}
}
