package vn.englishconversation.learning.controller;

import static vn.englishconversation.learning.dto.NotebookData.*;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.learning.service.NotebookService;

@RestController
@RequestMapping("/api/v1/learning/notebook")
@Profile("!bootstrap & !g1 & !g2")
public class NotebookController {
  private final NotebookService notebook;

  public NotebookController(NotebookService notebook) {
    this.notebook = notebook;
  }

  @GetMapping
  public Page<Card> cards(@AuthenticationPrincipal AccountPrincipal owner,
      @RequestParam(required = false) Status status, @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "12") int size) {
    return notebook.cards(owner, status, page, size);
  }

  @GetMapping("/spaced")
  public ReviewQueue spaced(@AuthenticationPrincipal AccountPrincipal owner,
      @RequestParam(defaultValue = "20") int limit) {
    return notebook.queue(owner, limit);
  }

  @PostMapping("/from-language-item/{id}")
  public Card save(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return notebook.save(owner, id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    notebook.delete(owner, id);
  }

  @GetMapping("/{id}")
  public Card detail(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return notebook.detail(owner, id);
  }

  @PostMapping("/{id}/attempts")
  public Attempt attempt(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id,
      @Valid @RequestBody ReviewInput input) {
    return notebook.review(owner, id, input);
  }
}
