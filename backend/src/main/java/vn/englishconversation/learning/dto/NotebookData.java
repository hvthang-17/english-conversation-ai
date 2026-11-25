package vn.englishconversation.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public final class NotebookData {
  private NotebookData() {}

  public enum Status { NEEDS_REVIEW, REMEMBERED }

  public enum Mode { FLASHCARD, QUICK_QUIZ }

  public record Card(String id, String sourceLanguageItemId, String itemType, String expression,
                     String meaningVi, String exampleEn, String ipa, Status status, String lastReviewedAt) {}

  public record Page<T>(List<T> items, int page, int size, long total) {}

  public record Attempt(String id, Mode mode, Status result, Boolean correct, String reviewedAt) {}

  public record ReviewInput(@NotNull Mode mode, Status result,
                            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String clientRequestId,
                            @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String optionId) {}

  public record QuizOption(String id, String meaningVi) implements Serializable {}

  public record Quiz(String id, String expression, List<QuizOption> options, Instant expiresAt) {}

  /** Server-side snapshot stored in the authenticated JDBC session. */
  public record QuizState(long ownerId, long cardId, String id, String expression,
                          List<QuizOption> options, String correctOptionId, Instant expiresAt)
      implements Serializable {
    public Quiz publicView() {
      return new Quiz(id, expression, options, expiresAt);
    }
  }
}
