package vn.englishconversation.auth.job;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.englishconversation.common.repository.JdbcStore;
import java.util.Optional;
import vn.englishconversation.learning.service.LearningAccountCleanup;
import vn.englishconversation.profile.service.AvatarService;
import vn.englishconversation.pronunciation.service.PronunciationService;

@Component
@Profile("!bootstrap")
public class AccountDeletionWorker {
  private final JdbcStore db;
  private final Optional<LearningAccountCleanup> learning;
  private final AvatarService avatars;
  private final Optional<PronunciationService> pronunciation;
  private final TransactionTemplate tx;

  public AccountDeletionWorker(
      JdbcStore db,
      Optional<LearningAccountCleanup> learning,
      AvatarService avatars,
      Optional<PronunciationService> pronunciation,
      PlatformTransactionManager manager) {
    this.db = db;
    this.learning = learning;
    this.avatars = avatars;
    this.pronunciation = pronunciation;
    tx = new TransactionTemplate(manager);
  }

  @Scheduled(fixedDelayString = "${app.account.cleanup-ms:300000}")
  public void sweep() {
    for (var row :
        db.rows(
            "SELECT id FROM account_deletion_jobs WHERE completed_at IS NULL AND"
                + " next_attempt_at<=UTC_TIMESTAMP(6) ORDER BY id LIMIT 50")) {
      long id = row.number("id");
      try {
        tx.executeWithoutResult(s -> clean(id));
      } catch (RuntimeException failure) {
        db.update(
            "UPDATE account_deletion_jobs SET"
                + " status='RETRY',attempt_count=attempt_count+1,error_code='CLEANUP_FAILED',next_attempt_at=UTC_TIMESTAMP(6)+INTERVAL"
                + " 5 MINUTE WHERE id=? AND completed_at IS NULL",
            id);
      }
    }
    db.update(
        "DELETE FROM account_deletion_jobs WHERE completed_at<UTC_TIMESTAMP(6)-INTERVAL 8 DAY");
  }

  private void clean(long id) {
    var job =
        db.find(
            "SELECT * FROM account_deletion_jobs WHERE id=? AND completed_at IS NULL FOR UPDATE",
            id);
    if (job.isEmpty()) return;
    long user = job.get().number("subject_user_id");
    var account =
        db.find(
            "SELECT id FROM users WHERE id=? AND deletion_requested_at IS NOT NULL FOR UPDATE",
            user);
    if (account.isPresent()) {
      pronunciation.ifPresent(service -> service.clearAccount(user));
      learning.ifPresent(cleanup -> cleanup.remove(user));
      String key =
          db.find("SELECT avatar_storage_key FROM learner_profiles WHERE user_id=?", user)
              .map(r -> r.text("avatar_storage_key"))
              .orElse(null);
      if (!avatars.remove(key)) throw new IllegalStateException("Avatar cleanup pending");
      db.update("DELETE FROM users WHERE id=?", user);
    }
    db.update(
        "UPDATE account_deletion_jobs SET"
            + " status='COMPLETED',completed_at=UTC_TIMESTAMP(6),error_code=NULL WHERE id=?",
        id);
  }
}
