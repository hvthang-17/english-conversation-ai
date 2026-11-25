package vn.englishconversation.learning.controller;

import static vn.englishconversation.learning.dto.NotebookData.*;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
  private static final String QUIZ_PREFIX = NotebookController.class.getName() + ".quiz.";
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

  @PostMapping("/from-language-item/{id}")
  public Card save(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    return notebook.save(owner, id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id) {
    notebook.delete(owner, id);
  }

  @GetMapping("/{id}/attempts")
  public Page<Attempt> history(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
    return notebook.history(owner, id, page, size);
  }

  @PostMapping("/{id}/quiz")
  public Quiz quiz(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id, HttpSession session) {
    var question = notebook.quiz(owner, id);
    pruneQuestions(session);
    session.setAttribute(QUIZ_PREFIX + question.id(), question);
    return question.publicView();
  }

  @PostMapping("/{id}/attempts")
  public Attempt attempt(@AuthenticationPrincipal AccountPrincipal owner, @PathVariable long id,
      @Valid @RequestBody ReviewInput input, HttpSession session) {
    var question = (QuizState) session.getAttribute(QUIZ_PREFIX + input.clientRequestId());
    var saved = notebook.review(owner, id, input, question);
    if (input.mode() == Mode.QUICK_QUIZ) session.removeAttribute(QUIZ_PREFIX + input.clientRequestId());
    return saved;
  }

  private static void pruneQuestions(HttpSession session) {
    Instant now = Instant.now();
    List<QuizState> active = new ArrayList<>();
    var names = session.getAttributeNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      if (name.startsWith(QUIZ_PREFIX) && session.getAttribute(name) instanceof QuizState question) {
        if (question.expiresAt().isAfter(now)) active.add(question);
        else session.removeAttribute(name);
      }
    }
    active.sort(Comparator.comparing(QuizState::expiresAt));
    for (int i = 0; i <= active.size() - 12; i++)
      session.removeAttribute(QUIZ_PREFIX + active.get(i).id());
  }
}
