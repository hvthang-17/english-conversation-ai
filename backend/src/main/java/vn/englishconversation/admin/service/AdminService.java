package vn.englishconversation.admin.service;

import java.util.HashSet;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.admin.dto.*;
import vn.englishconversation.admin.dto.AdminCatalog.*;
import vn.englishconversation.admin.repository.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.user.dto.UserResponse;

@Service
@Profile("!bootstrap")
public class AdminService {
  private final AdminStore store;
  private final AdminAudit audit;
  private final AccountService accounts;

  public AdminService(AdminStore store, AdminAudit audit, AccountService accounts) {
    this.store = store;
    this.audit = audit;
    this.accounts = accounts;
  }

  public AdminPage<UserResponse> users(String email, int page, int size) {
    validatePage(page, size);
    String query = email.strip().toLowerCase(Locale.ROOT);
    if (query.length() > 254)
      throw new ApiException(400, "INVALID_SEARCH", "Từ khóa email tối đa 254 ký tự.");
    return store.users(query, page, size);
  }

  public AdminPage<Topic> topics(int page, int size) {
    validatePage(page, size);
    return store.topics(page, size);
  }

  public AdminPage<Scenario> scenarios(long topicId, int page, int size) {
    validatePage(page, size);
    topic(topicId, false);
    return store.scenarios(topicId, page, size);
  }

  public AdminOverview overview() { return store.overview(); }

  @Transactional
  public Topic saveTopic(AccountPrincipal actor, Long id, TopicInput input) {
    accounts.requireAdminForWrite(actor);
    if (id != null) topic(id, true);
    long target;
    try {
      target = store.saveTopic(id, input);
    } catch (DuplicateKeyException conflict) {
      throw codeTaken();
    }
    audit.catalog(actor.id(), "TOPIC", target, id == null);
    return topic(target, false);
  }

  @Transactional
  public Scenario saveScenario(AccountPrincipal actor, long topicId, Long id, ScenarioInput input) {
    accounts.requireAdminForWrite(actor);
    // Serialize visibility and level updates for scenarios in the same topic.
    Topic topic = topic(topicId, true);
    if (id != null) scenario(topicId, id);
    if (new HashSet<>(input.levels()).size() != input.levels().size())
      throw new ApiException(400, "DUPLICATE_LEVEL", "Mỗi trình độ chỉ được chọn một lần.");
    if ("ACTIVE".equals(input.status()) && (!topic.active() || input.levels().isEmpty()))
      throw new ApiException(422, "SCENARIO_CANNOT_ACTIVATE",
          "Để công khai tình huống, hãy mở chủ đề và chọn ít nhất một trình độ.");
    long target;
    try {
      target = store.saveScenario(actor.id(), topicId, id, input);
    } catch (DuplicateKeyException conflict) {
      throw codeTaken();
    }
    audit.catalog(actor.id(), "SCENARIO", target, id == null);
    return scenario(topicId, target);
  }

  private Topic topic(long id, boolean lock) {
    return store.topic(id, lock)
        .orElseThrow(() -> new ApiException(404, "TOPIC_NOT_FOUND", "Không tìm thấy chủ đề."));
  }

  private Scenario scenario(long topicId, long id) {
    return store.scenario(topicId, id)
        .orElseThrow(() -> new ApiException(404, "SCENARIO_NOT_FOUND", "Không tìm thấy tình huống trong chủ đề."));
  }

  private static void validatePage(int page, int size) {
    if (page < 0 || size < 1 || size > 50)
      throw new ApiException(400, "INVALID_PAGE", "Trang phải từ 0 và kích thước từ 1 đến 50.");
  }

  private static ApiException codeTaken() {
    return new ApiException(409, "CATALOG_CODE_TAKEN", "Mã này đã được sử dụng.");
  }
}
