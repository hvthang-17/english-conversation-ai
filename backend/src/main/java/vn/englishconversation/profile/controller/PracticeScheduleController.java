package vn.englishconversation.profile.controller;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.profile.service.PracticeScheduleService;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/v1/profile/me/practice-schedule")
public class PracticeScheduleController {
  private final PracticeScheduleService schedules;

  public PracticeScheduleController(PracticeScheduleService schedules) {
    this.schedules = schedules;
  }

  @GetMapping
  public org.springframework.http.ResponseEntity<?> get(
      @AuthenticationPrincipal AccountPrincipal owner) {
    var schedule = schedules.get(owner.id());
    if (schedule == null)
      return org.springframework.http.ResponseEntity.ok()
          .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
          .body("null");
    return org.springframework.http.ResponseEntity.ok(schedule);
  }

  @PutMapping
  public PracticeScheduleService.View save(
      @AuthenticationPrincipal AccountPrincipal owner,
      @Valid @RequestBody PracticeScheduleService.Input input) {
    return schedules.save(owner, input);
  }
}
