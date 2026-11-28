package vn.englishconversation.lesson.service;

import java.util.List;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.lesson.repository.LessonMaterialsRepository.DialogueSource;
import vn.englishconversation.lesson.dto.LessonMaterials;
import vn.englishconversation.lesson.dto.LessonMaterials.Dialogue;
import vn.englishconversation.lesson.dto.LessonMaterials.Line;
import vn.englishconversation.lesson.repository.LessonMaterialsRepository;

@Service
@FeatureEnabled(FeatureEnabled.Feature.MATERIALS)
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

  private Dialogue dialogue(DialogueSource row) {
    try {
      List<Line> lines = mapper.readValue(row.linesJson(), new TypeReference<>() {});
      SampleDialogueFormat.validate(lines);
      return new Dialogue(row.id(), row.title(), lines);
    } catch (tools.jackson.core.JacksonException | IllegalArgumentException e) {
      throw new ApiException(503, "MATERIAL_UNAVAILABLE", "Học liệu này đang được kiểm tra lại.");
    }
  }
}
