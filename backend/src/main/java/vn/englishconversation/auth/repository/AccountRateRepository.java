package vn.englishconversation.auth.repository;

import org.springframework.stereotype.Repository;
import vn.englishconversation.config.features.FeatureEnabled;
import vn.englishconversation.common.repository.JdbcStore;

/** Persistence only: caller retains transaction, lock ordering, claim/version and cleanup policy. */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class AccountRateRepository {
  private final JdbcStore db;
  public AccountRateRepository(JdbcStore db) { this.db = db; }
  public record Bucket(long age,long gap,long requestCount) {}
  public void ensureBucket(String hash) {
    db.update("INSERT INTO account_rate_limits VALUES"
                          + " (?,UTC_TIMESTAMP(6),TIMESTAMP('2000-01-01'),0) ON DUPLICATE KEY"
                          + " UPDATE bucket_key=bucket_key", hash);
  }

  public java.util.Optional<Bucket> lockBucket(String hash) {
    return db.find("SELECT *,TIMESTAMPDIFF(SECOND,window_start,UTC_TIMESTAMP(6)) AS"
                                  + " age,TIMESTAMPDIFF(SECOND,last_request_at,UTC_TIMESTAMP(6)) AS"
                                  + " gap FROM account_rate_limits WHERE bucket_key=? FOR UPDATE", hash).map(row -> new Bucket(row.number("age"), row.number("gap"), row.number("request_count")));
  }

  public void resetWindow(String hash) {
    db.update("UPDATE account_rate_limits SET"
                            + " window_start=UTC_TIMESTAMP(6),request_count=0 WHERE bucket_key=?", hash);
  }

  public void recordRequest(String hash) {
    db.update("UPDATE account_rate_limits SET"
                          + " last_request_at=UTC_TIMESTAMP(6),request_count=request_count+1 WHERE"
                          + " bucket_key=?", hash);
  }
}
