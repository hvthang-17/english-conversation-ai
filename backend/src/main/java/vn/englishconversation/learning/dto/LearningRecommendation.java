package vn.englishconversation.learning.dto;

public record LearningRecommendation(String type, String id, String title, String level, String topic,
    String reasonCode, String reason) {
  public enum Focus { LISTENING, PRONUNCIATION, CONVERSATION, REVIEW }
}
