package vn.englishconversation.admin.controller;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import vn.englishconversation.admin.dto.AdminPage;
import vn.englishconversation.admin.dto.MaterialKind;
import vn.englishconversation.admin.dto.LessonCatalogOptions;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.admin.service.MaterialAdminService;
import vn.englishconversation.auth.security.AccountPrincipal;

@RestController
@RequestMapping("/api/v1/admin/materials/{kind}")
@Profile("g6")
public class MaterialAdminController {
  private final MaterialAdminService materials;

  public MaterialAdminController(MaterialAdminService materials) { this.materials = materials; }

  @GetMapping
  public AdminPage<Map<String, Object>> list(@PathVariable MaterialKind kind,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size,
      @RequestParam(required = false) String expressionKind) {
    return materials.list(kind, page, size, expressionKind);
  }

  @GetMapping("/lesson-catalog-options")
  public LessonCatalogOptions lessonCatalogOptions(@PathVariable MaterialKind kind) {
    if (kind != MaterialKind.LESSONS) throw new ApiException(404, "MATERIAL_NOT_FOUND", "Không tìm thấy danh mục.");
    return materials.lessonCatalogOptions();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@AuthenticationPrincipal AccountPrincipal actor,
      @PathVariable MaterialKind kind, @RequestBody Map<String, JsonNode> fields) {
    return materials.save(actor, kind, null, fields);
  }

  @PutMapping("/{id}")
  public Map<String, Object> update(@AuthenticationPrincipal AccountPrincipal actor,
      @PathVariable MaterialKind kind, @PathVariable long id, @RequestBody Map<String, JsonNode> fields) {
    return materials.save(actor, kind, id, fields);
  }

  @PostMapping("/{id}/{action:publish|archive}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void change(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable MaterialKind kind,
      @PathVariable long id, @PathVariable String action) {
    materials.change(actor, kind, id, action.equals("publish"));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable MaterialKind kind,
      @PathVariable long id) {
    materials.deleteDraft(actor, kind, id);
  }
}
