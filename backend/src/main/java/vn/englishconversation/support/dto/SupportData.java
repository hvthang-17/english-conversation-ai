package vn.englishconversation.support.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class SupportData {
  private SupportData() {}

  public enum Kind { FEEDBACK, TECHNICAL, CONTENT_REPORT }
  public enum Status { OPEN, IN_REVIEW, RESOLVED, CLOSED }
  public enum SourceType { LESSON, AI_MESSAGE, FEEDBACK }

  public record Source(@NotNull SourceType type, @Positive long id) {}
  public record Preview(String excerpt, String revision) {}
  public record Create(@NotNull Kind kind, @NotBlank @Size(max=200) String title,
      @NotBlank @Size(max=4000) String body, @Size(max=1) List<@NotNull @Valid Source> sources,
      @Size(max=64) String previewRevision, @NotNull UUID clientRequestId) {
    public Source source() { return sources == null || sources.isEmpty() ? null : sources.getFirst(); }
  }
  public record Reply(@NotBlank @Size(max=4000) String body, @NotNull UUID clientRequestId) {}
  public record Transition(@NotNull Status status) {}
  public record Assignment(@Positive Long adminId, @Positive Long expectedAdminId) {}
  public record Assignee(String id, String email) {}
  public record Ticket(String id, Kind kind, Status status, String title, String excerpt, boolean sourceRemoved,
      String createdAt, String updatedAt) {}
  public record AdminTicket(Ticket ticket, String assignedAdminId) {}
  public record Message(String id, String authorRole, String body, String createdAt) {}
  public record Page<T>(List<T> items, int page, int size, long total) {}
}
