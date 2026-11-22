package vn.englishconversation.scenario.dto;

import java.util.List;

public record ScenarioSummary(
    String id,
    String code,
    String topicId,
    String topicName,
    String title,
    String description,
    List<String> levels) {}
