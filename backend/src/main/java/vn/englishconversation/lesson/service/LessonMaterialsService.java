package vn.englishconversation.lesson.service;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.common.repository.JdbcStore.Row;
import vn.englishconversation.lesson.dto.LessonMaterials;
import vn.englishconversation.lesson.dto.LessonMaterials.Dialogue;
import vn.englishconversation.lesson.dto.LessonMaterials.Line;
import vn.englishconversation.lesson.repository.LessonMaterialsRepository;

@Service
@Profile("!bootstrap & !g1 & !g2")
public class LessonMaterialsService {
  private final LessonMaterialsRepository materials;
  private final ObjectMapper mapper;

  public LessonMaterialsService(LessonMaterialsRepository materials, ObjectMapper mapper) {
    this.materials = materials;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public LessonMaterials get(long lessonId, long ownerId) {
    if (!materials.visible(lessonId))
      throw new ApiException(404, "LESSON_NOT_FOUND", "Bài học không còn khả dụng.");
    return new LessonMaterials(materials.items(lessonId, ownerId),
        materials.dialogues(lessonId).stream().map(this::dialogue).toList());
  }

  private Dialogue dialogue(Row row) {
    try {
      List<Line> lines = mapper.readValue(row.text("lines_json"), new TypeReference<>() {});
      SampleDialogueFormat.validate(lines);
      return new Dialogue(row.text("id"), row.text("title"), lines);
    } catch (tools.jackson.core.JacksonException | IllegalArgumentException e) {
      throw new ApiException(503, "MATERIAL_UNAVAILABLE", "Học liệu này đang được kiểm tra lại.");
    }
  }
}
