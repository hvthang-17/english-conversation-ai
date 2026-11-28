package vn.englishconversation.learning.service;
import java.util.Optional;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.lesson.service.LessonCatalogService;
import vn.englishconversation.learning.service.NotebookService;
import vn.englishconversation.listening.service.ListeningAttempts;
import vn.englishconversation.pronunciation.service.PronunciationService;

@Service
@FeatureEnabled(FeatureEnabled.Feature.FOUNDATION)
public class FoundationLearningCleanupService {
  private final Optional<LessonCatalogService> lessons;
  private final Optional<NotebookService> notebook;
  private final Optional<ListeningAttempts> listening;
  private final Optional<PronunciationService> pronunciation;

  public FoundationLearningCleanupService(Optional<LessonCatalogService> lessons, Optional<NotebookService> notebook,
      Optional<ListeningAttempts> listening, Optional<PronunciationService> pronunciation, vn.englishconversation.config.features.FeatureSet features) {
    require(features.enabled(FeatureEnabled.Feature.CATALOG), lessons.isPresent());
    require(features.enabled(FeatureEnabled.Feature.MATERIALS), notebook.isPresent());
    require(features.enabled(FeatureEnabled.Feature.LISTENING), listening.isPresent());
    require(features.enabled(FeatureEnabled.Feature.PRONUNCIATION), pronunciation.isPresent());
    this.lessons = lessons;
    this.notebook = notebook;
    this.listening = listening;
    this.pronunciation = pronunciation;
  }

  @Transactional
  public void deleteData(AccountPrincipal owner) {
    pronunciation.ifPresent(service -> service.clearLearningData(owner.id()));
    lessons.ifPresent(catalog -> catalog.clear(owner));
    notebook.ifPresent(cards -> cards.clear(owner));
    listening.ifPresent(attempts -> attempts.clear(owner));
  }

  private static void require(boolean enabled, boolean present) {
    if (enabled != present) throw new IllegalStateException("Cleanup collaborator does not match the selected feature preset");
  }
}
