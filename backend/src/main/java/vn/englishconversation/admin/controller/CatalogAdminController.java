package vn.englishconversation.admin.controller;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.admin.dto.*;
import vn.englishconversation.admin.dto.AdminCatalog.*;
import vn.englishconversation.admin.service.AdminService;
import vn.englishconversation.auth.security.AccountPrincipal;

@RestController
@RequestMapping("/api/v1/admin")
@Profile("!bootstrap")
public class CatalogAdminController {
  private final AdminService admin;

  public CatalogAdminController(AdminService admin) { this.admin = admin; }

  @GetMapping("/overview")
  public AdminOverview overview() { return admin.overview(); }

  @GetMapping("/topics")
  public AdminPage<Topic> topics(@RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "12") int size) {
    return admin.topics(page, size);
  }

  @PostMapping("/topics")
  @ResponseStatus(HttpStatus.CREATED)
  public Topic createTopic(@AuthenticationPrincipal AccountPrincipal actor,
      @Valid @RequestBody TopicInput input) {
    return admin.saveTopic(actor, null, input);
  }

  @PutMapping("/topics/{id}")
  public Topic updateTopic(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id,
      @Valid @RequestBody TopicInput input) {
    return admin.saveTopic(actor, id, input);
  }

  @GetMapping("/topics/{topicId}/scenarios")
  public AdminPage<Scenario> scenarios(@PathVariable long topicId,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
    return admin.scenarios(topicId, page, size);
  }

  @PostMapping("/topics/{topicId}/scenarios")
  @ResponseStatus(HttpStatus.CREATED)
  public Scenario createScenario(@AuthenticationPrincipal AccountPrincipal actor,
      @PathVariable long topicId, @Valid @RequestBody ScenarioInput input) {
    return admin.saveScenario(actor, topicId, null, input);
  }

  @PutMapping("/topics/{topicId}/scenarios/{id}")
  public Scenario updateScenario(@AuthenticationPrincipal AccountPrincipal actor,
      @PathVariable long topicId, @PathVariable long id, @Valid @RequestBody ScenarioInput input) {
    return admin.saveScenario(actor, topicId, id, input);
  }
}
