package vn.englishconversation.lesson.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LessonMaterials(List<Item> items, List<Dialogue> dialogues) {
  public record Item(String id, String kind, String expression, String ipa, String meaningVi,
                     String exampleEn, String exampleVi, String savedCardId) {}

  public record Dialogue(String id, String title, List<Line> lines) {}

  public record Line(@JsonProperty("line_no") int number,
                     @JsonProperty("speaker_label") String speaker,
                     @JsonProperty("text_en") String english,
                     @JsonProperty("text_vi") String vietnamese,
                     @JsonProperty("voice_key") String voiceKey) {}
}
