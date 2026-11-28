package vn.englishconversation.learning.repository;

import static vn.englishconversation.learning.dto.LearningData.*;

import java.util.List;
import java.util.Optional;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.LEARNING)
public class LearningStore {
  private final JdbcStore db;

  public LearningStore(JdbcStore db) {
    this.db = db;
  }

  public boolean lockAccount(long user, long version) {
    return db.find(
            "SELECT id FROM users WHERE id=? AND status='ACTIVE' AND deletion_requested_at IS NULL AND security_version=? FOR UPDATE",
            user,
            version)
        .isPresent();
  }

  public void deletePlans(long owner) {
    db.update("DELETE FROM learning_plans WHERE user_id=?", owner);
  }


}
