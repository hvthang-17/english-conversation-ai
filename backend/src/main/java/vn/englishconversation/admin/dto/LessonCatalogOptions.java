package vn.englishconversation.admin.dto;

import java.util.List;

public record LessonCatalogOptions(List<Topic> topics, List<Scenario> scenarios, List<Lesson> lessons) {
  public record Lesson(String id, String title, String topicId, String level, boolean active) {}
  public record Topic(String id, String name, boolean active) {}
  public record Scenario(String id, String title, String topicId, String topicName, boolean active, List<String> levels) {}
}
