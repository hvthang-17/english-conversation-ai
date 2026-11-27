package vn.englishconversation.learning.dto;

public record DifficultyAdvice(Suggestion suggestion) {
  public record Suggestion(String currentLevel, String suggestedLevel, int sampleSize, int threshold) {}
}
