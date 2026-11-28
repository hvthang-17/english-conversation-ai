package vn.englishconversation.lesson.controller;

import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.lesson.dto.LessonResponse;
import vn.englishconversation.lesson.dto.LessonResponse.BookmarkKind;
import vn.englishconversation.lesson.dto.LessonResponse.Page;
import vn.englishconversation.lesson.service.LessonCatalogService;

@RestController
@RequestMapping("/api/v1/lessons")
@FeatureEnabled(FeatureEnabled.Feature.CATALOG)
public class LessonCatalogController {
  private final LessonCatalogService lessons;

  public LessonCatalogController(LessonCatalogService lessons) {
    this.lessons = lessons;
  }

  @GetMapping
  public Page list(@AuthenticationPrincipal AccountPrincipal owner,
      @RequestParam(required = false) String q, @RequestParam(required = false) String level,
      @RequestParam(required = false) String topicId, @RequestParam(required = false) String objectiveCode,
      @RequestParam(required = false) String scenarioId, @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "12") int size) {
    return lessons.list(owner, q, level, topicId, objectiveCode, scenarioId, page, size);
  }

  @GetMapping("/{id}")
  public LessonResponse detail(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return lessons.detail(owner, id);
  }

  @GetMapping("/saved")
  public Page saved(@AuthenticationPrincipal AccountPrincipal owner, @RequestParam BookmarkKind kind,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
    return lessons.saved(owner, kind, page, size);
  }

  @PutMapping("/{id}/bookmarks/{kind}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void save(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id,
      @PathVariable BookmarkKind kind) {
    lessons.save(owner, id, kind);
  }

  @DeleteMapping("/{id}/bookmarks/{kind}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id,
      @PathVariable BookmarkKind kind) {
    lessons.remove(owner, id, kind);
  }
}
