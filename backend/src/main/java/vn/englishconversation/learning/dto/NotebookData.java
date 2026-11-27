package vn.englishconversation.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

public final class NotebookData {
  private NotebookData() {}

  public enum Status { NEEDS_REVIEW, REMEMBERED }

  public enum Mode { FLASHCARD, QUICK_QUIZ, SPACED_REVIEW }

  public enum Rating { AGAIN, HARD, GOOD, EASY }

  public record Card(String id, String sourceLanguageItemId, String itemType, String expression,
                     String meaningVi, String exampleEn, String ipa, Status status, String lastReviewedAt,
                     String partOfSpeech) {}

  public record Page<T>(List<T> items, int page, int size, long total) {}

  public record Attempt(String id, Mode mode, Status result, Boolean correct, String reviewedAt,
                        Rating rating, String algorithmVersion, Long scheduleVersion,
                        Integer intervalDays, String nextReviewAt) {}

  public record ScheduledCard(Card card, String nextReviewAt, long scheduleVersion) {}

  public record ReviewQueue(long dueCount, long newCount, long newEligibleCount, int newRemainingToday,
                            String nextDueAt, Instant nextLocalDayAt, String timeZone,
                            List<ScheduledCard> items) {}

  public record ReviewInput(@NotNull Mode mode, Status result,
                            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String clientRequestId,
                            @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String optionId,
                            Rating rating, @Min(0) Long scheduleVersion) {}

}
