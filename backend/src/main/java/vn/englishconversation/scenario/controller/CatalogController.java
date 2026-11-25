package vn.englishconversation.scenario.controller;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.scenario.dto.*;
import vn.englishconversation.scenario.service.CatalogService;

@RestController
@RequestMapping("/api/v1")
@Profile("!bootstrap & !g1")
public class CatalogController {
  private final CatalogService catalog;

  public CatalogController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/topics")
  public List<TopicResponse> topics(@RequestParam(required = false) String level) {
    return catalog.topics(level);
  }

  @GetMapping("/scenarios")
  public ScenarioPage list(
      @RequestParam(required = false) String level,
      @RequestParam(required = false) String topicId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "12") int size) {
    return catalog.list(level, topicId, page, size);
  }

  @GetMapping("/scenarios/{id}")
  public ScenarioDetail detail(
      @PathVariable String id, @RequestParam(required = false) String level) {
    return catalog.detail(id, level);
  }
}
