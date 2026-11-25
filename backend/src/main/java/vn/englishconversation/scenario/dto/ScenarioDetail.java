package vn.englishconversation.scenario.dto;

import java.util.List;

public record ScenarioDetail(
    String id,
    String code,
    String topicId,
    String topicName,
    String title,
    String description,
    String learnerRole,
    String aiRole,
    String communicationGoal,
    String contextDescription,
    List<String> levels,
    List<ScenarioRole> roles) {}
