package vn.englishconversation.profile.dto;

import java.util.List;

public record ProfileResponse(
    String userId,
    String displayName,
    String selectedLevel,
    String learningGoal,
    Short dailyGoalMinutes,
    List<String> topicIds, String avatarUrl) {}
