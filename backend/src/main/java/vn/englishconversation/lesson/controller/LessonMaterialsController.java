package vn.englishconversation.lesson.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.lesson.dto.LessonMaterials;
import vn.englishconversation.lesson.service.LessonMaterialsService;

@RestController
@Profile("!bootstrap & !g1 & !g2")
public class LessonMaterialsController {
  private final LessonMaterialsService materials;

  public LessonMaterialsController(LessonMaterialsService materials) {
    this.materials = materials;
  }

  @GetMapping("/api/v1/lessons/{id}/materials")
  public LessonMaterials get(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return materials.get(id, owner.id());
  }
}
