package vn.englishconversation.scenario.controller;

import java.util.List;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.englishconversation.scenario.dto.TopicResponse;
import vn.englishconversation.scenario.repository.CatalogRepository;

@RestController
@FeatureEnabled(FeatureEnabled.Feature.ACCOUNT_ONLY)
public class FoundationTopicsController {
  private final CatalogRepository catalog;

  public FoundationTopicsController(CatalogRepository catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/api/v1/topics")
  public List<TopicResponse> topics() {
    return catalog.activeTopics();
  }
}
