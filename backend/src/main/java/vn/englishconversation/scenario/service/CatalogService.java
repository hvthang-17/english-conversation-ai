package vn.englishconversation.scenario.service;

import java.util.List;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.scenario.dto.*;
import vn.englishconversation.scenario.repository.CatalogRepository;

@Service
@FeatureEnabled(FeatureEnabled.Feature.CORE)
@Transactional(readOnly = true)
public class CatalogService {
  private final CatalogRepository catalog;

  public CatalogService(CatalogRepository catalog) {
    this.catalog = catalog;
  }

  public static String level(String value) {
    if (value != null && !value.equals("A2") && !value.equals("B1"))
      throw new ApiException(400, "INVALID_LEVEL", "Chọn trình độ A2 hoặc B1.");
    return value;
  }

  public static long id(String value) {
    try {
      long id = Long.parseLong(value);
      if (id <= 0) throw new NumberFormatException();
      return id;
    } catch (NumberFormatException e) {
      throw new ApiException(400, "INVALID_ID", "Định danh không hợp lệ.");
    }
  }

  public List<TopicResponse> topics(String level) {
    return level == null ? catalog.activeTopics() : catalog.topics(level(level));
  }

  public ScenarioPage list(String level, String topicId, int page, int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 50)
      throw new ApiException(
          400, "INVALID_PAGINATION", "Trang từ 0 đến 100000, kích thước từ 1 đến 50.");
    return catalog.list(level(level), topicId == null ? null : id(topicId), page, size);
  }

  public ScenarioDetail detail(String id, String level) {
    return catalog
        .detail(id(id), level(level))
        .orElseThrow(
            () ->
                new ApiException(
                    404, "SCENARIO_NOT_FOUND", "Tình huống không tồn tại hoặc không còn phù hợp."));
  }
}
