package vn.englishconversation.learning.service;

import static vn.englishconversation.learning.dto.LearningPlanData.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.common.repository.JdbcStore.Row;
import vn.englishconversation.learning.dto.LearningRecommendation.Focus;
import vn.englishconversation.learning.repository.LearningPlanRepository;
import vn.englishconversation.learning.repository.LearningStore;

@Service
@Profile("g6 | (!bootstrap & !g1 & !g2 & !g3 & !g4 & !g5)")
public class LearningPlanService {
  private final LearningPlanRepository plans;
  private final LearningStore learning;
  private final RecommendationService recommendations;

  public LearningPlanService(LearningPlanRepository plans, LearningStore learning, RecommendationService recommendations) {
    this.plans = plans;
    this.learning = learning;
    this.recommendations = recommendations;
  }

  @Transactional(readOnly=true)
  public List<Summary> list(AccountPrincipal owner, int page) {
    if (page < 0 || page > 1000000) throw invalid("Trang không hợp lệ.");
    return plans.list(owner.id(), page * 20).stream().map(LearningPlanService::summary).toList();
  }

  @Transactional(readOnly=true)
  public Detail detail(AccountPrincipal owner, long id) {
    return view(owned(owner, id, false));
  }

  @Transactional
  public Detail create(AccountPrincipal owner, Create input) {
    lockOwner(owner);
    LocalDate end = endDate(input.settings());
    var active = plans.active(owner.id());
    if (active.isPresent()) {
      var current = active.get();
      if (!Objects.equals(input.replaceId(), current.number("id"))
          || !Objects.equals(input.version(), current.number("version"))) throw stale();
      plans.archive(current.number("id"));
    } else if (input.replaceId() != null || input.version() != null) {
      throw stale();
    }
    return detail(owner, plans.insert(owner.id(), input.settings(), end));
  }

  @Transactional
  public Detail edit(AccountPrincipal owner, long id, Edit input) {
    writable(owner, id, input.version());
    LocalDate start = input.settings().startDate();
    LocalDate end = endDate(input.settings());
    if (plans.items(id).stream().map(row -> date(row, "due_date"))
        .anyMatch(day -> day.isBefore(start) || day.isAfter(end)))
      throw invalid("Khoảng ngày mới phải chứa các mục học đã xếp lịch.");
    plans.edit(id, input.settings(), end);
    return updated(owner, id);
  }

  @Transactional
  public Detail addItem(AccountPrincipal owner, long id, AddItem input) {
    var plan = writable(owner, id, input.version());
    requireDate(plan, input.dueDate());
    var items = plans.items(id);
    if (items.size() >= 100) throw invalid("Kế hoạch đã có 100 mục học.");
    requirePosition(items, input.dueDate(), input.position(), 0);
    var source = input.source();
    String title = title(owner, source);
    String reason = "MANUAL";
    if (Boolean.TRUE.equals(input.recommended())) {
      String focus = plan.text("focus_skill");
      reason = recommendations.suggest(owner.id(), focus == null ? null : Focus.valueOf(focus)).stream()
          .filter(item -> item.type().equals(source.type().name()) && item.id().equals(source.id().toString()))
          .findFirst().orElseThrow(() -> invalid("Gợi ý đã thay đổi. Hãy tải lại danh sách gợi ý.")).reasonCode();
    }
    plans.shift(id, input.dueDate(), input.position(), 1, 0);
    plans.insertItem(id, input, source, title, reason);
    return updated(owner, id);
  }

  @Transactional
  public Detail editItem(AccountPrincipal owner, long id, long itemId, EditItem input) {
    var plan = writable(owner, id, input.version());
    var current = plannedItem(id, itemId);
    requireDate(plan, input.dueDate());
    requirePosition(plans.items(id), input.dueDate(), input.position(), itemId);
    if (input.source() != null) plans.replaceSource(itemId, input.source(), title(owner, input.source()));
    plans.shift(id, date(current, "due_date"), (int) current.number("position") + 1, -1, itemId);
    plans.shift(id, input.dueDate(), input.position(), 1, itemId);
    plans.moveItem(itemId, input);
    return updated(owner, id);
  }

  @Transactional
  public Detail finishItem(AccountPrincipal owner, long id, long itemId, FinishItem input) {
    writable(owner, id, input.version());
    var current = plannedItem(id, itemId);
    if (input.result() == Result.COMPLETED) {
      String lesson = current.text("lesson_id");
      String card = current.text("review_item_id");
      if (lesson == null && card == null) throw invalid("Mục học đã mất nguồn. Hãy thay mục trước khi hoàn thành.");
      title(owner, lesson != null ? new Source(SourceType.LESSON, Long.valueOf(lesson))
          : new Source(SourceType.REVIEW_ITEM, Long.valueOf(card)));
    }
    plans.finishItem(itemId, input.result());
    return updated(owner, id);
  }

  private Row writable(AccountPrincipal owner, long id, long version) {
    lockOwner(owner);
    var plan = owned(owner, id, true);
    if (plan.number("version") != version) throw stale();
    if (!"ACTIVE".equals(plan.text("status")))
      throw new ApiException(409, "PLAN_CLOSED", "Kế hoạch đã đóng.");
    return plan;
  }

  private void lockOwner(AccountPrincipal owner) {
    if (!learning.lockAccount(owner.id(), owner.securityVersion()))
      throw new ApiException(401, "SESSION_REVOKED", "Vui lòng đăng nhập lại.");
  }

  private Row owned(AccountPrincipal owner, long id, boolean lock) {
    return plans.plan(owner.id(), id, lock)
        .orElseThrow(() -> new ApiException(404, "PLAN_NOT_FOUND", "Không tìm thấy kế hoạch."));
  }

  private Row plannedItem(long plan, long item) {
    var row = plans.item(plan, item)
        .orElseThrow(() -> new ApiException(404, "PLAN_ITEM_NOT_FOUND", "Không tìm thấy mục học."));
    if (!"PLANNED".equals(row.text("status")))
      throw new ApiException(409, "PLAN_ITEM_CLOSED", "Mục học đã hoàn thành hoặc đã bỏ qua.");
    return row;
  }

  private String title(AccountPrincipal owner, Source source) {
    return plans.source(owner.id(), source).map(row -> row.text("title"))
        .orElseThrow(() -> new ApiException(404, "PLAN_SOURCE_UNAVAILABLE", "Nguồn học không còn khả dụng."));
  }

  private Detail updated(AccountPrincipal owner, long id) {
    plans.bump(id);
    return detail(owner, id);
  }

  private Detail view(Row plan) {
    return new Detail(summary(plan), plans.items(plan.number("id")).stream().map(row -> new Item(
        row.text("id"), date(row, "due_date"), (int) row.number("position"),
        row.text("lesson_id"), row.text("review_item_id"), row.text("source_title_snapshot"), row.flag("available"),
        row.text("reason_code"), row.text("status"), row.text("completed_iso"))).toList());
  }

  private static Summary summary(Row row) {
    return new Summary(row.text("id"), row.text("goal"), row.text("focus_skill"), date(row, "start_date"),
        date(row, "end_date"), row.text("time_zone"), row.text("status"), row.number("version"));
  }

  private static LocalDate date(Row row, String field) { return LocalDate.parse(row.text(field)); }

  private static LocalDate endDate(Settings input) {
    if (input.days() != 1 && input.days() != 7) throw invalid("Kế hoạch phải dài 1 hoặc 7 ngày.");
    if (input.startDate().getYear() < 1000 || input.startDate().getYear() > 9999)
      throw invalid("Ngày bắt đầu nằm ngoài phạm vi hỗ trợ.");
    var end = input.startDate().plusDays(input.days() - 1);
    if (end.getYear() > 9999) throw invalid("Ngày kết thúc nằm ngoài phạm vi hỗ trợ.");
    return end;
  }

  private static void requireDate(Row plan, LocalDate day) {
    if (day.isBefore(date(plan, "start_date")) || day.isAfter(date(plan, "end_date")))
      throw invalid("Ngày học phải nằm trong phạm vi kế hoạch.");
  }

  private static void requirePosition(List<Row> items, LocalDate day, int position, long excluded) {
    long count = items.stream().filter(row -> row.number("id") != excluded && date(row, "due_date").equals(day)).count();
    if (position > count + 1) throw invalid("Vị trí vượt số mục của ngày đã chọn.");
  }

  private static ApiException invalid(String message) { return new ApiException(422, "PLAN_INVALID", message); }
  private static ApiException stale() { return new ApiException(409, "PLAN_VERSION_CONFLICT", "Kế hoạch đã thay đổi. Hãy tải lại."); }
}
