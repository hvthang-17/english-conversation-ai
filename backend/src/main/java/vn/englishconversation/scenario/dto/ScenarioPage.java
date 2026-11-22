package vn.englishconversation.scenario.dto;

import java.util.List;

public record ScenarioPage(List<ScenarioSummary> content, int page, int size, long totalElements) {}
