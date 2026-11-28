package vn.englishconversation.learning.repository;

import static vn.englishconversation.learning.dto.LearningPlanData.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.PLANS)
public class LearningPlanRepository {
  private final JdbcStore db;

  public LearningPlanRepository(JdbcStore db) { this.db = db; }

  private Optional<Row> activeRows(long owner) {
    return db.find("SELECT * FROM learning_plans WHERE user_id=? AND status='ACTIVE' FOR UPDATE", owner);
  }

  private Optional<Row> planRows(long owner, long id, boolean lock) {
    return db.find("SELECT * FROM learning_plans WHERE user_id=? AND id=?" + (lock ? " FOR UPDATE" : ""), owner, id);
  }

  private List<Row> listRows(long owner, int offset) {
    return db.rows("SELECT * FROM learning_plans WHERE user_id=? ORDER BY id DESC LIMIT 20 OFFSET ?", owner, offset);
  }

  public String timeZone(long owner) {
    return db.find("SELECT time_zone FROM practice_schedules WHERE user_id=?", owner)
        .map(row -> row.text("time_zone")).orElse("Asia/Ho_Chi_Minh");
  }

  public long insert(long owner, Settings input, LocalDate end) {
    return db.insert("""
        INSERT INTO learning_plans(user_id,goal,focus_skill,start_date,end_date,time_zone,created_at,updated_at)
        VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """, owner, input.goal().strip(), focus(input), input.startDate(), end, timeZone(owner));
  }

  public void archive(long id) {
    db.update("UPDATE learning_plans SET status='ARCHIVED',version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?", id);
  }

  public void edit(long id, Settings input, LocalDate end) {
    db.update("UPDATE learning_plans SET goal=?,focus_skill=?,start_date=?,end_date=? WHERE id=?",
        input.goal().strip(), focus(input), input.startDate(), end, id);
  }

  public void bump(long id) {
    db.update("UPDATE learning_plans SET version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?", id);
  }

  private List<Row> itemsRows(long plan) {
    return db.rows("""
        SELECT i.*, COALESCE(c.id IS NOT NULL OR (l.status='ACTIVE' AND t.is_active),FALSE) AS available,
               DATE_FORMAT(i.completed_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS completed_iso
        FROM learning_plan_items i
        LEFT JOIN lessons l ON l.id=i.lesson_id LEFT JOIN topics t ON t.id=l.topic_id
        LEFT JOIN review_items c ON c.id=i.review_item_id
        WHERE i.plan_id=? ORDER BY i.due_date,i.position,i.id
        """, plan);
  }

  private Optional<Row> itemRows(long plan, long id) {
    return db.find("SELECT * FROM learning_plan_items WHERE plan_id=? AND id=?", plan, id);
  }

  private Optional<Row> sourceRows(long owner, Source source) {
    return switch (source.type()) {
      case LESSON -> db.find("""
          SELECT l.title FROM lessons l JOIN topics t ON t.id=l.topic_id
          WHERE l.id=? AND l.status='ACTIVE' AND t.is_active FOR SHARE
          """, source.id());
      case REVIEW_ITEM -> db.find("SELECT expression AS title FROM review_items WHERE id=? AND user_id=? FOR SHARE",
          source.id(), owner);
    };
  }

  public void shift(long plan, LocalDate date, int at, int delta, long excluded) {
    db.update("""
        UPDATE learning_plan_items SET position=position+?
        WHERE plan_id=? AND due_date=? AND position>=? AND id<>?
        """, delta, plan, date, at, excluded);
  }

  public void insertItem(long plan, AddItem input, Source source, String title, String reason) {
    db.update("""
        INSERT INTO learning_plan_items(plan_id,due_date,position,lesson_id,review_item_id,source_title_snapshot,reason_code)
        VALUES (?,?,?,?,?,?,?)
        """, plan, input.dueDate(), input.position(), lesson(source), card(source), title, reason);
  }

  public void moveItem(long id, EditItem input) {
    db.update("UPDATE learning_plan_items SET due_date=?,position=? WHERE id=?", input.dueDate(), input.position(), id);
  }

  public void replaceSource(long id, Source source, String title) {
    db.update("""
        UPDATE learning_plan_items SET lesson_id=?,review_item_id=?,source_title_snapshot=?,reason_code='MANUAL' WHERE id=?
        """, lesson(source), card(source), title, id);
  }

  public void finishItem(long id, Result result) {
    db.update("""
        UPDATE learning_plan_items SET status=?,
          completed_at=CASE WHEN ?='COMPLETED' THEN UTC_TIMESTAMP(6) ELSE NULL END WHERE id=?
        """, result.name(), result.name(), id);
  }

  private static Long lesson(Source source) { return source.type() == SourceType.LESSON ? source.id() : null; }
  private static Long card(Source source) { return source.type() == SourceType.REVIEW_ITEM ? source.id() : null; }
  private static String focus(Settings settings) { return settings.focusSkill() == null ? null : settings.focusSkill().name(); }

  public record Plan(long id,String goal,String focusSkill,String startDate,String endDate,String timeZone,String status,long version) {}
  private static Plan mapPlan(Row row) { return new Plan(row.number("id"),row.text("goal"),row.text("focus_skill"),row.text("start_date"),row.text("end_date"),row.text("time_zone"),row.text("status"),row.number("version")); }
  public record PlanItem(long id,String dueDate,long position,String lessonId,String reviewItemId,String sourceTitleSnapshot,boolean available,String reasonCode,String status,String completedIso) {}
  private static PlanItem mapPlanItem(Row row) { return new PlanItem(row.number("id"),row.text("due_date"),row.number("position"),row.text("lesson_id"),row.text("review_item_id"),row.text("source_title_snapshot"),Boolean.TRUE.equals(row.data().get("available")) || row.data().get("available") instanceof Number n && n.longValue()!=0,row.text("reason_code"),row.text("status"),row.text("completed_iso")); }
  public record SourceTitle(String title) {}
  private static SourceTitle mapSourceTitle(Row row) { return new SourceTitle(row.text("title")); }
  public java.util.Optional<Plan> active(long owner) { return activeRows(owner).map(LearningPlanRepository::mapPlan); }
  public java.util.Optional<Plan> plan(long owner,long id,boolean lock) { return planRows(owner,id,lock).map(LearningPlanRepository::mapPlan); }
  public java.util.List<Plan> list(long owner,int offset) { return listRows(owner,offset).stream().map(LearningPlanRepository::mapPlan).toList(); }
  public java.util.List<PlanItem> items(long plan) { return itemsRows(plan).stream().map(LearningPlanRepository::mapPlanItem).toList(); }
  public java.util.Optional<PlanItem> item(long plan,long id) { return itemRows(plan,id).map(LearningPlanRepository::mapPlanItem); }
  public java.util.Optional<SourceTitle> source(long owner,Source source) { return sourceRows(owner,source).map(LearningPlanRepository::mapSourceTitle); }
}
