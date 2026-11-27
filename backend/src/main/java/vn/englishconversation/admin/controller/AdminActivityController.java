package vn.englishconversation.admin.controller;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.englishconversation.admin.dto.AdminActivity;
import vn.englishconversation.admin.dto.AdminPage;
import vn.englishconversation.admin.dto.AdminMetrics;
import vn.englishconversation.admin.service.AdminActivityService;
import vn.englishconversation.admin.service.AdminMetricsService;

@RestController
@RequestMapping("/api/v1/admin")
@Profile("g6")
public class AdminActivityController {
  private final AdminActivityService activity;
  private final AdminMetricsService metrics;
  public AdminActivityController(AdminActivityService activity,AdminMetricsService metrics) {
    this.activity=activity;
    this.metrics=metrics;
  }

  @GetMapping("/metrics")
  public ResponseEntity<AdminMetrics> metrics(@RequestParam(defaultValue="7") int days) {
    return noStore(metrics.metrics(days));
  }

  @GetMapping(value = "/audit", params = "!page")
  public ResponseEntity<List<AdminActivity>> audit() {
    return noStore(activity.list(true, 0, 50, "", "").items());
  }

  @GetMapping(value = "/ai-attempts", params = "!page")
  public ResponseEntity<List<AdminActivity>> attempts() {
    return noStore(activity.list(false, 0, 50, "", "").items());
  }

  @GetMapping(value = "/audit", params = "page")
  public ResponseEntity<AdminPage<AdminActivity>> auditPage(@RequestParam int page,
      @RequestParam(defaultValue = "12") int size, @RequestParam(defaultValue = "") String q,
      @RequestParam(required=false) Integer days) {
    return noStore(activity.list(true, page, size, q, "", days));
  }

  @GetMapping(value = "/ai-attempts", params = "page")
  public ResponseEntity<AdminPage<AdminActivity>> attemptsPage(@RequestParam int page,
      @RequestParam(defaultValue = "12") int size, @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String status, @RequestParam(required=false) Integer days) {
    return noStore(activity.list(false, page, size, q, status, days));
  }

  private <T> ResponseEntity<T> noStore(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
