package vn.englishconversation.profile.repository;

import org.springframework.stereotype.Repository;
import vn.englishconversation.config.features.FeatureEnabled;
import vn.englishconversation.common.repository.JdbcStore;

/** Persistence only: caller retains transaction, lock ordering, claim/version and cleanup policy. */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class AvatarRepository {
  private final JdbcStore db;
  public AvatarRepository(JdbcStore db) { this.db = db; }
  public record AvatarKey(String avatarStorageKey) {}
  public java.util.Optional<AvatarKey> currentKey(long owner) {
    return db.find("SELECT avatar_storage_key FROM learner_profiles WHERE user_id=?", owner).map(row -> new AvatarKey(row.text("avatar_storage_key")));
  }

  public long profileCount(long owner) {
    return db.count("SELECT COUNT(*) FROM learner_profiles WHERE user_id=?", owner);
  }

  public void setKey(String key,long owner) {
    db.update("UPDATE learner_profiles SET avatar_storage_key=?,updated_at=UTC_TIMESTAMP(6)"
                          + " WHERE user_id=?", key, owner);
  }

  public void clearKey(long owner) {
    db.update("UPDATE learner_profiles SET avatar_storage_key=NULL,updated_at=UTC_TIMESTAMP(6)"
                      + " WHERE user_id=?", owner);
  }

  public long referenceCount(String key) {
    return db.count("SELECT COUNT(*) FROM learner_profiles WHERE avatar_storage_key=?", key);
  }
}
