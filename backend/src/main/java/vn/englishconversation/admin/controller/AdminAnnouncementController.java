package vn.englishconversation.admin.controller;

import static vn.englishconversation.admin.dto.AdminAnnouncement.*;

import jakarta.validation.Valid;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.admin.dto.AdminPage;
import vn.englishconversation.admin.service.AdminAnnouncementService;
import vn.englishconversation.auth.security.AccountPrincipal;

@RestController
@FeatureEnabled(FeatureEnabled.Feature.ADMIN)
@RequestMapping("/api/v1/admin/announcements")
public class AdminAnnouncementController {
  private final AdminAnnouncementService announcements;

  public AdminAnnouncementController(AdminAnnouncementService announcements) { this.announcements=announcements; }

  @GetMapping
  public AdminPage<View> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size) {
    return announcements.list(page,size);
  }

  @PostMapping("/preview")
  public Preview preview(@Valid @RequestBody Draft draft) { return announcements.preview(draft); }

  @PostMapping
  public View create(@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody Save input) {
    return announcements.create(actor,input);
  }

  @PutMapping("/{id}")
  public View update(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@Valid @RequestBody Save input) {
    return announcements.update(actor,id,input);
  }

  @PostMapping("/{id}/publish")
  public View publish(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@Valid @RequestBody Publish input) {
    return announcements.publish(actor,id,input.version());
  }

  @PostMapping("/{id}/archive")
  public View archive(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {
    return announcements.archive(actor,id);
  }
}
