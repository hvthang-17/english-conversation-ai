package vn.englishconversation.admin.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.admin.dto.AdminActivity;
import vn.englishconversation.admin.dto.AdminPage;
import vn.englishconversation.common.repository.JdbcStore;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.ADMIN)
public class AdminActivityStore {
  private static final String AUDIT = """
      SELECT id,action AS activity,target_type AS subject,CAST(target_id AS CHAR) AS reference,
        CAST(actor_user_id AS CHAR) AS actor,NULL AS status,NULL AS latency,created_at AS occurred
      FROM admin_audit_logs
      """;
  private static final String ATTEMPTS = """
      SELECT a.id,r.task_type AS activity,a.provider AS subject,a.model_name AS reference,
        NULL AS actor,a.status,a.latency_ms AS latency,a.started_at AS occurred
      FROM ai_attempts a JOIN ai_requests r ON r.id=a.ai_request_id
      """;
  private static final Map<String, String> LABELS = Map.ofEntries(
      Map.entry("USER_ACCESS_UPDATED", "Cập nhật quyền truy cập"), Map.entry("TOPIC_CREATED", "Tạo chủ đề"),
      Map.entry("TOPIC_UPDATED", "Cập nhật chủ đề"), Map.entry("SCENARIO_CREATED", "Tạo tình huống"),
      Map.entry("SCENARIO_UPDATED", "Cập nhật tình huống"), Map.entry("CHAT", "Hội thoại"),
      Map.entry("HINT", "Gợi ý trả lời"), Map.entry("FEEDBACK", "Nhận xét"),
      Map.entry("TRANSCRIBE", "Chép lời"), Map.entry("PRONUNCIATION", "Phát âm"),
      Map.entry("USER", "Tài khoản"), Map.entry("TOPIC", "Chủ đề"), Map.entry("SCENARIO", "Tình huống"),
      Map.entry("SUPPORT_ASSIGNED", "Phân công phiếu"), Map.entry("SUPPORT_REPLIED", "Trả lời phiếu"),
      Map.entry("SUPPORT_STATUS_UPDATED", "Đổi trạng thái phiếu"), Map.entry("SUPPORT_TICKET", "Phiếu hỗ trợ"),
      Map.entry("ANNOUNCEMENT_CREATED", "Tạo bản nháp"), Map.entry("ANNOUNCEMENT_UPDATED", "Cập nhật bản nháp"),
      Map.entry("ANNOUNCEMENT_PUBLISHED", "Công bố thông báo"), Map.entry("ANNOUNCEMENT_ARCHIVED", "Ẩn thông báo"),
      Map.entry("ANNOUNCEMENT", "Thông báo"),
      Map.entry("RUNNING", "Đang xử lý"), Map.entry("SUCCEEDED", "Thành công"),
      Map.entry("FAILED", "Thất bại"), Map.entry("UNKNOWN", "Chưa rõ kết quả"));
  private final JdbcStore db;

  public AdminActivityStore(JdbcStore db) { this.db = db; }

  public AdminPage<AdminActivity> list(boolean audit, int page, int size, String query, String status) {
    return list(audit,page,size,query,status,null);
  }

  public AdminPage<AdminActivity> list(boolean audit, int page, int size, String query, String status, Integer days) {
    String from = " FROM (" + (audit ? AUDIT : ATTEMPTS) + ") activity_log WHERE 1=1";
    var args = new ArrayList<Object>();
    if (days!=null) {
      var until=java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
      from += " AND occurred>=? AND occurred<?";
      args.add(until.minusDays(days));
      args.add(until);
    }
    if (!query.isEmpty()) {
      String needle = query.toLowerCase(Locale.ROOT);
      String pattern = "%" + needle.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
      from += " AND (LOWER(CONCAT_WS(' ',id,activity,subject,reference,actor,status,occurred)) LIKE ? ESCAPE '!'";
      args.add(pattern);
      var codes = LABELS.entrySet().stream().filter(entry -> entry.getValue().toLowerCase(Locale.ROOT).contains(needle))
          .map(Map.Entry::getKey).sorted().toList();
      if (!codes.isEmpty()) {
        String placeholders = String.join(",", java.util.Collections.nCopies(codes.size(), "?"));
        from += " OR activity IN (" + placeholders + ") OR subject IN (" + placeholders + ") OR status IN (" + placeholders + ")";
        for (int index = 0; index < 3; index++) args.addAll(codes);
      }
      from += ")";
    }
    if (!status.isEmpty()) { from += " AND status=?"; args.add(status); }
    long total = db.count("SELECT COUNT(*)" + from, args.toArray());
    var rowArgs = new ArrayList<>(args);
    rowArgs.add(size); rowArgs.add((long) page * size);
    List<AdminActivity> items = db.rows("SELECT *" + from + " ORDER BY id DESC LIMIT ? OFFSET ?", rowArgs.toArray())
        .stream().map(row -> new AdminActivity(row.text("id"), row.text("activity"), row.text("subject"),
            row.text("reference"), row.text("actor"), row.text("status"), row.text("latency"), row.text("occurred"))).toList();
    return new AdminPage<>(items, page, size, total);
  }
}
