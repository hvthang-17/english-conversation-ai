package vn.englishconversation.admin.controller;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.englishconversation.common.repository.JdbcStore;

@RestController
@RequestMapping("/api/v1/admin")
@Profile("g6")
public class AdminActivityController {
  private final JdbcStore db;

  public AdminActivityController(JdbcStore db) {
    this.db = db;
  }

  @GetMapping("/audit")
  public ResponseEntity<List<Activity>> audit() {
    return response("""
        SELECT id,action AS activity,target_type AS subject,CAST(target_id AS CHAR) AS reference,
          CAST(actor_user_id AS CHAR) AS actor,NULL AS status,NULL AS latency,created_at AS occurred
        FROM admin_audit_logs ORDER BY id DESC LIMIT 50
        """);
  }

  @GetMapping("/ai-attempts")
  public ResponseEntity<List<Activity>> attempts() {
    return response("""
        SELECT a.id,r.task_type AS activity,a.provider AS subject,a.model_name AS reference,
          NULL AS actor,a.status,a.latency_ms AS latency,a.started_at AS occurred
        FROM ai_attempts a JOIN ai_requests r ON r.id=a.ai_request_id
        ORDER BY a.id DESC LIMIT 50
        """);
  }

  private ResponseEntity<List<Activity>> response(String sql) {
    var items = db.rows(sql).stream().map(row -> new Activity(row.text("id"), row.text("activity"),
        row.text("subject"), row.text("reference"), row.text("actor"), row.text("status"),
        row.text("latency"), row.text("occurred"))).toList();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(items);
  }

  public record Activity(String id, String activity, String subject, String reference, String actor,
                         String status, String latency, String occurred) {}
}
