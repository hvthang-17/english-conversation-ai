package db.migration;

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
import vn.englishconversation.listening.dto.ListeningData.Kind;
import vn.englishconversation.listening.dto.ListeningData.Question;
import vn.englishconversation.listening.dto.ListeningData.Segment;
import vn.englishconversation.listening.service.ListeningRules;

public class V12__starter_listening_exercises extends BaseJavaMigration {
  private static final String RESOURCE = "/content/starter-listening-exercises.json";

  @Override
  public Integer getChecksum() {
    var crc = new CRC32();
    crc.update(content().replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
    return (int) crc.getValue();
  }

  @Override
  public void migrate(Context context) throws Exception {
    var mapper = new ObjectMapper();
    List<Seed> rows = mapper.readValue(content(), new TypeReference<>() {});
    var lessons = new HashSet<Long>();
    var ids = new HashSet<Long>();
    var types = new HashSet<Kind>();
    for (var row : rows) {
      if (!lessons.add(row.lessonId()) || !ids.add(row.id()))
        throw new IllegalArgumentException("Duplicate starter listening exercise");
      ListeningRules.validate(row.title(), row.instructions(), row.ttsVoiceKey(), row.transcriptEn(),
          row.segments(), row.questions());
      row.questions().forEach(question -> types.add(question.type()));
    }
    if (!lessons.equals(Set.of(101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L))
        || !types.equals(Set.of(Kind.values()))) throw new IllegalArgumentException("Incomplete listening seed");

    try (var insert = context.getConnection().prepareStatement("""
        INSERT INTO listening_exercises
          (id,lesson_id,title,instructions,tts_voice_key,transcript_en,translation_vi,
           script_segments_json,questions_json,status,created_at,updated_at)
        VALUES (?,?,?,?,?,?,?,?,?,'DRAFT',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """)) {
      for (var row : rows) {
        insert.setLong(1, row.id());
        insert.setLong(2, row.lessonId());
        insert.setString(3, row.title());
        insert.setString(4, row.instructions());
        insert.setString(5, row.ttsVoiceKey());
        insert.setString(6, row.transcriptEn());
        insert.setString(7, row.translationVi());
        insert.setString(8, row.segments() == null ? null : mapper.writeValueAsString(row.segments()));
        insert.setString(9, mapper.writeValueAsString(row.questions()));
        insert.addBatch();
      }
      insert.executeBatch();
    }
  }

  private static String content() {
    try (var file = Objects.requireNonNull(V12__starter_listening_exercises.class.getResourceAsStream(RESOURCE))) {
      return new String(file.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  public record Seed(long id, long lessonId, String title, String instructions, String ttsVoiceKey,
                     String transcriptEn, String translationVi, List<Segment> segments, List<Question> questions) {}
}
