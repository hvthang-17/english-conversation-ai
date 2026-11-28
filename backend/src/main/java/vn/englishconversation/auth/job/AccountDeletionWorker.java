package vn.englishconversation.auth.job;

import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.englishconversation.auth.repository.AccountDeletionRepository;
import java.util.Optional;
import vn.englishconversation.learning.service.LearningAccountCleanup;
import vn.englishconversation.profile.service.AvatarService;
import vn.englishconversation.pronunciation.service.PronunciationService;

@Component
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class AccountDeletionWorker {
  private final AccountDeletionRepository persistence;
  private final Optional<LearningAccountCleanup> learning;
  private final AvatarService avatars;
  private final Optional<PronunciationService> pronunciation;
  private final TransactionTemplate tx;

  public AccountDeletionWorker(AccountDeletionRepository persistence,
      Optional<LearningAccountCleanup> learning,
      AvatarService avatars,
      Optional<PronunciationService> pronunciation,
      PlatformTransactionManager manager) {
    this.persistence = persistence;
    this.learning = learning;
    this.avatars = avatars;
    this.pronunciation = pronunciation;
    tx = new TransactionTemplate(manager);
  }

  @Scheduled(fixedDelayString = "${app.account.cleanup-ms:300000}")
  public void sweep() {
    for (var row :
        persistence.dueJobs()) {
      long id = row.id();
      try {
        tx.executeWithoutResult(s -> clean(id));
      } catch (RuntimeException failure) {
        persistence.retryJob(id);
      }
    }
    persistence.purgeCompletedJobs();
  }

  private void clean(long id) {
    var job =
        persistence.lockJob(id);
    if (job.isEmpty()) return;
    long user = job.get().subjectUserId();
    var account =
        persistence.lockPendingAccount(user);
    if (account.isPresent()) {
      pronunciation.ifPresent(service -> service.clearAccount(user));
      learning.ifPresent(cleanup -> cleanup.remove(user));
      String key =
          persistence.avatarKey(user)
              .map(r -> r.avatarStorageKey())
              .orElse(null);
      if (!avatars.remove(key)) throw new IllegalStateException("Avatar cleanup pending");
      persistence.deleteAccount(user);
    }
    persistence.completeJob(id);
  }
}
