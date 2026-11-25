package vn.englishconversation.admin.dto;

import java.util.List;

public enum MaterialKind {
  TOPICS("topics", "code name description sort_order", "user_topic_interests:topic_id scenarios:topic_id lessons:topic_id"),
  LESSONS("lessons", "topic_id level scenario_id title summary objective_code learning_objective",
      "lesson_bookmarks:lesson_id language_items:lesson_id sample_dialogues:lesson_id listening_exercises:lesson_id pronunciation_drills:lesson_id"),
  WORDS("language_items", "lesson_id kind expression ipa meaning_vi example_en example_vi sort_order",
      "review_items:source_language_item_id"),
  DIALOGUES("sample_dialogues", "lesson_id title lines_json sort_order", ""),
  LISTENING("listening_exercises",
      "lesson_id title instructions tts_voice_key transcript_en translation_vi script_segments_json questions_json sort_order",
      "listening_attempts:exercise_id"),
  PRONUNCIATION("pronunciation_drills", "lesson_id reference_text locale mode sort_order", "pronunciation_attempts:drill_id");

  public final String table;
  public final List<String> fields;
  public final List<String> references;

  MaterialKind(String table, String fields, String references) {
    this.table = table;
    this.fields = List.of(fields.split(" "));
    this.references = references.isEmpty() ? List.of() : List.of(references.split(" "));
  }
}
