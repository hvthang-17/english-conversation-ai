package db.migration;

import static vn.englishconversation.lesson.service.SampleDialogueFormat.text;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.lesson.dto.LessonMaterials.Line;
import vn.englishconversation.lesson.service.SampleDialogueFormat;

public class V10__starter_language_materials extends BaseJavaMigration {
  private static final String RESOURCE = "/content/starter-language-materials.json";

  @Override
  public Integer getChecksum() {
    var crc = new CRC32();
    crc.update(content().replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
    return (int) crc.getValue();
  }

  @Override
  public void migrate(Context context) throws Exception {
    var mapper = new ObjectMapper();
    List<LessonSeed> lessons = mapper.readValue(content(), new TypeReference<>() {});
    validate(lessons);

    try (var items = context.getConnection().prepareStatement("""
        INSERT INTO language_items
           (lesson_id,kind,expression,ipa,meaning_vi,example_en,example_vi,sort_order,status,created_at,updated_at)
        VALUES (?,?,?,?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """);
         var dialogues = context.getConnection().prepareStatement("""
        INSERT INTO sample_dialogues (lesson_id,title,lines_json,sort_order,status,created_at,updated_at)
        VALUES (?,?,?,1,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """)) {
      for (var lesson : lessons) {
        int order = 1;
        for (var item : lesson.items()) {
          items.setLong(1, lesson.lessonId());
          items.setString(2, item.kind());
          items.setString(3, item.expression());
          items.setString(4, item.ipa());
          items.setString(5, item.meaningVi());
          items.setString(6, item.exampleEn());
          items.setString(7, item.exampleVi());
          items.setInt(8, order++);
          items.addBatch();
        }
        dialogues.setLong(1, lesson.lessonId());
        dialogues.setString(2, lesson.dialogue().title());
        dialogues.setString(3, mapper.writeValueAsString(lesson.dialogue().lines()));
        dialogues.addBatch();
      }
      items.executeBatch();
      dialogues.executeBatch();
    }
  }

  private static void validate(List<LessonSeed> lessons) {
    var ids = new HashSet<Long>();
    if (lessons == null || lessons.size() != 8) throw new IllegalArgumentException("Expected eight starter lessons");
    for (var lesson : lessons) {
      if (lesson == null || !ids.add(lesson.lessonId()) || lesson.items() == null
          || lesson.items().size() != 5 || lesson.dialogue() == null
          || !text(lesson.dialogue().title(), 200))
        throw new IllegalArgumentException("Invalid starter lesson structure");
      int words = 0;
      for (var item : lesson.items()) {
        if (item == null || item.kind() == null || !Set.of("WORD", "PHRASE").contains(item.kind())
            || !text(item.expression(), 200) || !text(item.meaningVi(), 500)
            || !text(item.exampleEn(), 500) || !text(item.exampleVi(), 500)
            || (item.ipa() != null && !text(item.ipa(), 240)))
          throw new IllegalArgumentException("Invalid language item for lesson " + lesson.lessonId());
        if ("WORD".equals(item.kind())) {
          words++;
          if (item.ipa() == null) throw new IllegalArgumentException("Starter words require IPA");
        }
      }
      if (words != 3) throw new IllegalArgumentException("Expected three words and two phrases per lesson");
      SampleDialogueFormat.validate(lesson.dialogue().lines());
    }
    if (!ids.equals(Set.of(101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L)))
      throw new IllegalArgumentException("Unexpected starter lesson IDs");
  }

  private static String content() {
    try (var file = Objects.requireNonNull(V10__starter_language_materials.class.getResourceAsStream(RESOURCE))) {
      return new String(file.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException error) {
      throw new UncheckedIOException(error);
    }
  }

  public record LessonSeed(long lessonId, List<ItemSeed> items, DialogueSeed dialogue) {}
  public record ItemSeed(String kind, String expression, String ipa, String meaningVi,
                         String exampleEn, String exampleVi) {}
  public record DialogueSeed(String title, List<Line> lines) {}
}
