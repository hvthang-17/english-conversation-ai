package vn.englishconversation.lesson.service;

import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.lesson.dto.LessonResponse;
import vn.englishconversation.lesson.dto.LessonResponse.BookmarkKind;
import vn.englishconversation.lesson.dto.LessonResponse.Page;
import vn.englishconversation.lesson.repository.LessonCatalogRepository;
import vn.englishconversation.scenario.service.CatalogService;
import vn.englishconversation.user.repository.UserRepository;

@Service
@Profile("!bootstrap & !g1")
public class LessonCatalogService {
  private final LessonCatalogRepository lessons;
  private final UserRepository users;

  public LessonCatalogService(LessonCatalogRepository lessons, UserRepository users) {
    this.lessons = lessons;
    this.users = users;
  }

  @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Page list(AccountPrincipal owner, String keyword, String level, String topic,
                   String objective, String scenario, int page, int size) {
    validatePage(page, size);
    String query = keyword == null ? "" : keyword.strip();
    if (query.length() > 120)
      throw new ApiException(400, "INVALID_SEARCH", "Từ khóa tối đa 120 ký tự.");
    if (objective != null && !objective.matches("[A-Z0-9_]{1,64}"))
      throw new ApiException(400, "INVALID_OBJECTIVE", "Mã mục tiêu không hợp lệ.");
    return lessons.catalog(owner.id(),
        query.isEmpty() ? null : query.toLowerCase(Locale.ROOT).replace('đ', 'd'),
        CatalogService.level(level), topic == null ? null : CatalogService.id(topic), objective,
        scenario == null ? null : CatalogService.id(scenario), page, size);
  }

  @Transactional(readOnly = true)
  public LessonResponse detail(AccountPrincipal owner, long id) {
    return lessons.detail(owner.id(), id).orElseThrow(LessonCatalogService::unavailable);
  }

  @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Page saved(AccountPrincipal owner, BookmarkKind kind, int page, int size) {
    validatePage(page, size);
    return lessons.saved(owner.id(), kind, page, size);
  }

  @Transactional
  public void save(AccountPrincipal owner, long lesson, BookmarkKind kind) {
    requireOwner(owner);
    if (!lessons.lockVisible(lesson)) throw unavailable();
    lessons.save(owner.id(), lesson, kind);
  }

  @Transactional
  public void remove(AccountPrincipal owner, long lesson, BookmarkKind kind) {
    requireOwner(owner);
    lessons.remove(owner.id(), lesson, kind);
  }

  @Transactional
  public void clear(AccountPrincipal owner) {
    requireOwner(owner);
    lessons.removeAll(owner.id());
  }

  private void requireOwner(AccountPrincipal owner) {
    users.lockById(owner.id())
        .filter(user -> AccountService.valid(user, owner))
        .orElseThrow(() -> new ApiException(401, "SESSION_REVOKED", "Vui lòng đăng nhập lại."));
  }

  private static void validatePage(int page, int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 50)
      throw new ApiException(400, "INVALID_PAGINATION", "Trang từ 0 đến 100000, kích thước từ 1 đến 50.");
  }

  private static ApiException unavailable() {
    return new ApiException(404, "LESSON_NOT_FOUND", "Bài học không còn khả dụng.");
  }
}
