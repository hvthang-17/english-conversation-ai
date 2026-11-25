package vn.englishconversation.listening.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public final class ListeningData {
  private ListeningData() {}

  public enum Kind { MULTIPLE_CHOICE, FILL_BLANK, REORDER, DICTATION }

  public record Option(String code, String text) {}
  public record Segment(@JsonProperty("line_no") int lineNo, @JsonProperty("speaker_label") String speakerLabel,
                        @JsonProperty("text_en") String textEn, @JsonProperty("voice_key") String voiceKey) {}
  public record Question(String code, Kind type, String prompt, List<Option> options,
                         List<String> accepted, String explanationVi) {
    public PublicQuestion publicView() {
      return new PublicQuestion(code, type, prompt, options == null ? List.of() : options);
    }
  }
  public record PublicQuestion(String code, Kind type, String prompt, List<Option> options) {}
  public record Exercise(String id, String title, String level, String instructions) {}
  public record ExercisePage(List<Exercise> items, int page, int size, long total) {}
  public record DraftExercise(String id, String lessonId, String title, String level, String status) {}
  public record Start(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String clientRequestId) {}
  public record Answers(@NotNull @PositiveOrZero Integer draftVersion,
                        @NotNull @Size(max = 12) Map<String, JsonNode> answers) {}
  public record Graded(String questionCode, JsonNode answerValue, int pointsAwarded,
                       List<String> accepted, String explanationVi) {}
  public record Result(int score, int maxScore, String transcriptEn, String translationVi, List<Graded> details) {}
  public record Attempt(String id, Exercise exercise, String status, int draftVersion,
                        List<PublicQuestion> questions, Map<String, JsonNode> answers,
                        Result result, String startedAt) {}
  public record History(String id, Exercise exercise, String status, Integer score, int maxScore, String startedAt) {}
  public record HistoryPage(List<History> items, int page, int size, long total) {}
}
