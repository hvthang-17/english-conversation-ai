package vn.englishconversation.lesson.service;

import java.util.List;
import java.util.Set;
import vn.englishconversation.lesson.dto.LessonMaterials.Line;

/** Shared content validation for the seed and published dialogue reads. */
public final class SampleDialogueFormat {
  private static final Set<String> VOICES = Set.of("en-us-voice-a-v1", "en-us-voice-b-v1");

  private SampleDialogueFormat() {}

  public static void validate(List<Line> lines) {
    if (lines == null || lines.size() < 4 || lines.size() > 6)
      throw new IllegalArgumentException("A sample dialogue must have 4–6 turns");
    for (int index = 0; index < lines.size(); index++) {
      Line line = lines.get(index);
      if (line == null || line.number() != index + 1)
        throw new IllegalArgumentException("Dialogue turn numbers must be consecutive from 1");
      if (!text(line.speaker(), 80) || !text(line.english(), 500) || !text(line.vietnamese(), 500))
        throw new IllegalArgumentException("Dialogue speaker and text are required within length limits");
      if (line.voiceKey() != null && !VOICES.contains(line.voiceKey()))
        throw new IllegalArgumentException("Unknown sample dialogue voice key");
    }
    if (lines.stream().map(Line::speaker).distinct().count() != 2)
      throw new IllegalArgumentException("A sample dialogue must have two speakers");
  }

  public static boolean text(String value, int maxLength) {
    return value != null && !value.isBlank() && value.length() <= maxLength;
  }
}
