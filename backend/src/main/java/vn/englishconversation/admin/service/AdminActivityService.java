package vn.englishconversation.admin.service;

import java.util.Set;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import vn.englishconversation.admin.dto.AdminActivity;
import vn.englishconversation.admin.dto.AdminPage;
import vn.englishconversation.admin.repository.AdminActivityStore;
import vn.englishconversation.common.exception.ApiException;

@Service
@FeatureEnabled(FeatureEnabled.Feature.ADMIN)
public class AdminActivityService {
  private final AdminActivityStore store;
  public AdminActivityService(AdminActivityStore store) { this.store = store; }

  public AdminPage<AdminActivity> list(boolean audit, int page, int size, String query, String status) {
    return list(audit,page,size,query,status,null);
  }

  public AdminPage<AdminActivity> list(boolean audit, int page, int size, String query, String status,Integer days) {
    if (days!=null) AdminMetricsService.validateDays(days);
    if (page < 0 || size < 1 || size > 50)
      throw new ApiException(400, "INVALID_PAGE", "Trang phải từ 0 và kích thước từ 1 đến 50.");
    String needle = query == null ? "" : query.trim();
    String state = status == null ? "" : status.trim();
    if (needle.length() > 120 || (!state.isEmpty() && (audit || !Set.of("RUNNING", "SUCCEEDED", "FAILED", "UNKNOWN").contains(state))))
      throw new ApiException(400, "INVALID_FILTER", "Kiểm tra từ khóa (tối đa 120 ký tự) và trạng thái tác vụ.");
    return store.list(audit, page, size, needle, state,days);
  }
}
