package vn.englishconversation.lesson.dto;

import java.util.List;

public record LessonResponse(
    String id,
    boolean available,
    String title,
    String summary,
    String topicId,
    String topicName,
    String level,
    String objectiveCode,
    String learningObjective,
    String scenarioId,
    String scenarioTitle,
    boolean favorite,
    boolean savedForLater) {

  public enum BookmarkKind { FAVORITE, SAVE_FOR_LATER }

  public record Page(List<LessonResponse> items, int page, int size, long total) {}
}
