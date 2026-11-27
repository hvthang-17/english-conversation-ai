package vn.englishconversation.learning.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public final class LearningData {
  private LearningData() {}

  public record ReviewAnswer(
      @NotNull @Pattern(regexp = "NEEDS_REVIEW|REMEMBERED") String result,
      @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String clientRequestId) {}

  public record Page<T>(List<T> items, int page, int size, long total) {}

  public record Card(
      String id,
      String sourceSuggestionId,
      String itemType,
      String expression,
      String meaningVi,
      String exampleEn,
      String status,
      String lastReviewedAt,
      String partOfSpeech) {}

  public record HistoryEntry(
      String id,
      String title,
      String level,
      String status,
      String startedAt,
      String endedAt,
      long learnerTurns,
      String feedbackStatus) {}

  public record Group(String label, long count) {}

  public record TopicActivity(String topic, long completedSessions, long learnerTurns) {}

  public record Recommendation(String scenarioId, String title, String level, String reason) {}

  public record Statistics(
      String timeZone,
      long completedSessions,
      long learnerTurns,
      long practiceDays,
      List<Group> recentDays,
      List<TopicActivity> topics,
      List<Group> errorGroups,
      List<Recommendation> recommendations) {}
}
