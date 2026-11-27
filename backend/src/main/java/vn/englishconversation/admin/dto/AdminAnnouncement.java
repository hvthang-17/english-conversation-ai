package vn.englishconversation.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AdminAnnouncement {
  private AdminAnnouncement() {}
  public enum Audience { LEARNER, ADMIN }
  public enum Level { A2, B1 }
  public record Draft(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=1000) String body,
      @NotNull Audience audienceRole, Level audienceLevel, Instant expiresAt) {}
  public record Save(@NotNull @jakarta.validation.Valid Draft draft, UUID clientRequestId,
      @PositiveOrZero Long version) {}
  public record Publish(@NotNull @PositiveOrZero Long version) {}
  public record Preview(Draft draft, long estimatedRecipients) {}
  public record View(String id, Draft draft, String status, long version, long recipients, Instant publishedAt) {}
}
