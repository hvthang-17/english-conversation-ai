package vn.englishconversation.admin.service;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
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

  public List<ScenarioVariant> scenarioVariants(long topicId, long id) {
    scenario(topicId, id);
    return store.scenarioVariants(id);
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
    Scenario existing = id == null ? null : scenario(topicId, id);
    if (existing != null && "DRAFT".equals(input.status()) && !"DRAFT".equals(existing.status()))
      throw new ApiException(409, "SCENARIO_NOT_DRAFT", "Tình huống đã phát hành có thể được ẩn.");
    if (new HashSet<>(input.levels()).size() != input.levels().size())
      throw new ApiException(400, "DUPLICATE_LEVEL", "Mỗi trình độ chỉ được chọn một lần.");
    List<ScenarioVariant> previous = id == null ? List.of() : store.scenarioVariants(id);
    List<ScenarioVariant> variants = input.roleVariants() == null ? previous : input.roleVariants();
    validateScenarioRoles(input.learnerRole(), input.aiRole(), input.aiInstruction(), variants);
    if ("ACTIVE".equals(input.status())) requirePublishable(topic, input.levels());
    long target;
    try {
      target = store.saveScenario(actor.id(), topicId, id, input);
    } catch (DuplicateKeyException conflict) {
      throw codeTaken();
    }
    if (input.roleVariants() != null) store.replaceScenarioVariants(target, previous, variants);
    audit.catalog(actor.id(), "SCENARIO", target, id == null);
    return scenario(topicId, target);
  }

  @Transactional
  public void removeScenario(AccountPrincipal actor, long topicId, long id, boolean archive) {
    accounts.requireAdminForWrite(actor);
    topic(topicId, true);
    String status = scenario(topicId, id).status();
    if (archive) {
      if ("ARCHIVED".equals(status)) return;
      if (!"ACTIVE".equals(status))
        throw new ApiException(409, "SCENARIO_NOT_ACTIVE", "Chỉ ẩn tình huống đang công khai.");
      store.setScenarioStatus(id, actor.id(), "ARCHIVED");
      audit.catalog(actor.id(), "SCENARIO", id, false);
      return;
    }
    if (!"DRAFT".equals(status))
      throw new ApiException(409, "SCENARIO_NOT_DRAFT", "Chỉ xóa được tình huống nháp. Hãy ẩn tình huống đã phát hành.");
    try {
      store.deleteScenario(id);
    } catch (DataIntegrityViolationException referenced) {
      throw new ApiException(409, "SCENARIO_IN_USE", "Tình huống còn được bài học hoặc buổi luyện tham chiếu.");
    }
    audit.catalogDeleted(actor.id(), "SCENARIO", id);
  }

  @Transactional
  public void restoreScenario(AccountPrincipal actor, long topicId, long id) {
    accounts.requireAdminForWrite(actor);
    Topic topic = topic(topicId, true);
    Scenario existing = scenario(topicId, id);
    if ("ACTIVE".equals(existing.status())) return;
    if (!"ARCHIVED".equals(existing.status()))
      throw new ApiException(409, "SCENARIO_NOT_ARCHIVED", "Chỉ bỏ ẩn được tình huống đã ẩn.");
    requirePublishable(topic, existing.levels());
    validateScenarioRoles(existing.learnerRole(), existing.aiRole(), existing.aiInstruction(), store.scenarioVariants(id));
    store.setScenarioStatus(id, actor.id(), "ACTIVE");
    audit.catalog(actor.id(), "SCENARIO", id, false);
  }

  private static void requirePublishable(Topic topic, List<String> levels) {
    if (!topic.active() || levels.isEmpty())
      throw new ApiException(422, "SCENARIO_CANNOT_ACTIVATE",
          "Để công khai tình huống, hãy mở chủ đề và chọn ít nhất một trình độ.");
  }

  private static void validateScenarioRoles(String learnerRole, String aiRole, String instruction,
      List<ScenarioVariant> variants) {
    var codes = new HashSet<String>();
    codes.add("DEFAULT");
    var pairs = new HashSet<List<String>>();
    pairs.add(List.of(normalizeRole(learnerRole), normalizeRole(aiRole)));
    validateInstruction(instruction);
    for (var variant : variants) {
      if (!codes.add(variant.code()))
        throw new ApiException(422, "SCENARIO_ROLE_CODE_DUPLICATE", "Mã biến thể vai phải khác nhau và không dùng DEFAULT.");
      if (!pairs.add(List.of(normalizeRole(variant.learnerRole()), normalizeRole(variant.aiRole()))))
        throw new ApiException(422, "SCENARIO_ROLE_PAIR_DUPLICATE", "Cặp vai người học và AI đã có trong tình huống.");
      validateInstruction(variant.aiInstruction());
    }
  }

  private static String normalizeRole(String role) {
    return Normalizer.normalize(role, Normalizer.Form.NFC).replaceAll("(?U)\\s+", " ").strip().toLowerCase(Locale.ROOT);
  }

  private static void validateInstruction(String instruction) {
    if (instruction.codePoints().anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t'))
      throw new ApiException(422, "SCENARIO_INSTRUCTION_INVALID", "Chỉ dẫn AI chứa ký tự điều khiển không hợp lệ.");
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
