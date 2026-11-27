package vn.englishconversation.support.controller;

import static vn.englishconversation.support.dto.SupportData.*;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.support.service.SupportService;

@RestController
@Profile("g6")
public class SupportController {
  private static final String USER = "/api/v1/support/tickets";
  private static final String ADMIN = "/api/v1/admin/support/tickets";
  private final SupportService support;

  public SupportController(SupportService support) { this.support = support; }

  @PostMapping(USER + "/preview")
  public Preview preview(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody Source source) {
    return support.preview(actor,source);
  }

  @PostMapping(USER)
  public Ticket create(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody Create input) {
    return support.create(actor,input);
  }

  @GetMapping(USER)
  public Page<Ticket> list(@AuthenticationPrincipal AccountPrincipal actor, @RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="12") int size) {
    return support.list(actor.id(),null,page,size);
  }

  @GetMapping(USER + "/{id}")
  public Ticket detail(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id) {
    return support.detail(id,actor.id());
  }

  @GetMapping(USER + "/{id}/messages")
  public Page<Message> messages(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id,
      @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
    return support.messages(id,actor.id(),page,size);
  }

  @PostMapping(USER + "/{id}/messages")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reply(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@Valid @RequestBody Reply input) {
    support.reply(actor,id,input,false);
  }

  @GetMapping(ADMIN)
  public Page<AdminTicket> queue(@RequestParam(required=false) Status status, @RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="12") int size) {
    return support.queue(status,page,size);
  }

  @GetMapping(ADMIN + "/assignees")
  public List<Assignee> assignees() { return support.assignees(); }

  @GetMapping(ADMIN + "/{id}")
  public AdminTicket adminDetail(@PathVariable long id) { return support.adminDetail(id); }

  @PatchMapping(ADMIN + "/{id}/assignment")
  public AdminTicket assign(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,
      @Valid @RequestBody Assignment input) {
    return support.assign(actor,id,input);
  }

  @GetMapping(ADMIN + "/{id}/messages")
  public Page<Message> adminMessages(@PathVariable long id, @RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="20") int size) {
    return support.messages(id,null,page,size);
  }

  @PostMapping(ADMIN + "/{id}/messages")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void adminReply(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@Valid @RequestBody Reply input) {
    support.reply(actor,id,input,true);
  }

  @PatchMapping(ADMIN + "/{id}/status")
  public AdminTicket advance(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@Valid @RequestBody Transition input) {
    return support.transition(actor,id,input.status());
  }
}
