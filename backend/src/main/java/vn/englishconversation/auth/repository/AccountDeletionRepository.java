package vn.englishconversation.auth.repository;

import org.springframework.stereotype.Repository;
import vn.englishconversation.config.features.FeatureEnabled;
import vn.englishconversation.common.repository.JdbcStore;

/** Persistence only: caller retains transaction, lock ordering, claim/version and cleanup policy. */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class AccountDeletionRepository {
  private final JdbcStore db;
  public AccountDeletionRepository(JdbcStore db) { this.db = db; }
  public record JobId(long id) {}
  public record DeletionJob(long subjectUserId) {}
  public record AccountId(long id) {}
  public record AvatarKey(String avatarStorageKey) {}
  public java.util.List<JobId> dueJobs() {
    return db.rows("SELECT id FROM account_deletion_jobs WHERE completed_at IS NULL AND"
                + " next_attempt_at<=UTC_TIMESTAMP(6) ORDER BY id LIMIT 50").stream().map(row -> new JobId(row.number("id"))).toList();
  }

  public void retryJob(long id) {
    db.update("UPDATE account_deletion_jobs SET"
                + " status='RETRY',attempt_count=attempt_count+1,error_code='CLEANUP_FAILED',next_attempt_at=UTC_TIMESTAMP(6)+INTERVAL"
                + " 5 MINUTE WHERE id=? AND completed_at IS NULL", id);
  }

  public void purgeCompletedJobs() {
    db.update("DELETE FROM account_deletion_jobs WHERE completed_at<UTC_TIMESTAMP(6)-INTERVAL 8 DAY");
  }

  public java.util.Optional<DeletionJob> lockJob(long id) {
    return db.find("SELECT * FROM account_deletion_jobs WHERE id=? AND completed_at IS NULL FOR UPDATE", id).map(row -> new DeletionJob(row.number("subject_user_id")));
  }

  public java.util.Optional<AccountId> lockPendingAccount(long owner) {
    return db.find("SELECT id FROM users WHERE id=? AND deletion_requested_at IS NOT NULL FOR UPDATE", owner).map(row -> new AccountId(row.number("id")));
  }

  public java.util.Optional<AvatarKey> avatarKey(long owner) {
    return db.find("SELECT avatar_storage_key FROM learner_profiles WHERE user_id=?", owner).map(row -> new AvatarKey(row.text("avatar_storage_key")));
  }

  public void deleteAccount(long owner) {
    db.update("DELETE FROM users WHERE id=?", owner);
  }

  public void completeJob(long id) {
    db.update("UPDATE account_deletion_jobs SET"
            + " status='COMPLETED',completed_at=UTC_TIMESTAMP(6),error_code=NULL WHERE id=?", id);
  }
}
