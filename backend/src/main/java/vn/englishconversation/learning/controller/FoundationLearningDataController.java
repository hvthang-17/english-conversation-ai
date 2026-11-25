package vn.englishconversation.learning.controller;

import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.lesson.service.LessonCatalogService;
import vn.englishconversation.learning.service.NotebookService;
import vn.englishconversation.listening.service.ListeningAttempts;
import vn.englishconversation.pronunciation.service.PronunciationService;

@RestController
@RequestMapping("/api/v1/learning")
@Profile({"g1", "g2", "g3", "g4", "g5"})
public class FoundationLearningDataController {
  private final Optional<LessonCatalogService> lessons;
  private final Optional<NotebookService> notebook;
  private final Optional<ListeningAttempts> listening;
  private final Optional<PronunciationService> pronunciation;

  public FoundationLearningDataController(Optional<LessonCatalogService> lessons, Optional<NotebookService> notebook,
      Optional<ListeningAttempts> listening, Optional<PronunciationService> pronunciation) {
    this.lessons = lessons;
    this.notebook = notebook;
    this.listening = listening;
    this.pronunciation = pronunciation;
  }

  @DeleteMapping("/data")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void deleteData(@AuthenticationPrincipal AccountPrincipal owner) {
    pronunciation.ifPresent(service -> service.clearLearningData(owner.id()));
    lessons.ifPresent(catalog -> catalog.clear(owner));
    notebook.ifPresent(cards -> cards.clear(owner));
    listening.ifPresent(attempts -> attempts.clear(owner));
  }

  @DeleteMapping("/history/{id}")
  public void deleteSession(@PathVariable long id) {
    throw new ApiException(404, "SESSION_NOT_FOUND", "Không tìm thấy buổi luyện tập.");
  }
}
