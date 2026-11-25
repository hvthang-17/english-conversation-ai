package vn.englishconversation.auth.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.englishconversation.auth.security.TokenCipher;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.common.repository.JdbcStore;

@Service
@Profile("!bootstrap")
public class AccountRateLimiter {
  private final JdbcStore db;
  private final TransactionTemplate rateTx;

  public AccountRateLimiter(JdbcStore db, PlatformTransactionManager manager) {
    this.db = db;
    rateTx = new TransactionTemplate(manager);
    rateTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public void check(String key, int limit, int cooldownSeconds) {
    boolean accepted =
        Boolean.TRUE.equals(
            rateTx.execute(
                s -> {
                  String hash = TokenCipher.hash(key);
                  db.update(
                      "INSERT INTO account_rate_limits VALUES"
                          + " (?,UTC_TIMESTAMP(6),TIMESTAMP('2000-01-01'),0) ON DUPLICATE KEY"
                          + " UPDATE bucket_key=bucket_key",
                      hash);
                  var row =
                      db.find(
                              "SELECT *,TIMESTAMPDIFF(SECOND,window_start,UTC_TIMESTAMP(6)) AS"
                                  + " age,TIMESTAMPDIFF(SECOND,last_request_at,UTC_TIMESTAMP(6)) AS"
                                  + " gap FROM account_rate_limits WHERE bucket_key=? FOR UPDATE",
                              hash)
                          .orElseThrow();
                  if (row.number("age") >= 3600)
                    db.update(
                        "UPDATE account_rate_limits SET"
                            + " window_start=UTC_TIMESTAMP(6),request_count=0 WHERE bucket_key=?",
                        hash);
                  if ((row.number("age") < 3600 && row.number("request_count") >= limit)
                      || row.number("gap") < cooldownSeconds) return false;
                  db.update(
                      "UPDATE account_rate_limits SET"
                          + " last_request_at=UTC_TIMESTAMP(6),request_count=request_count+1 WHERE"
                          + " bucket_key=?",
                      hash);
                  return true;
                }));
    if (!accepted) throw new ApiException(429, "RATE_LIMITED", "Vui lòng chờ trước khi thử lại.");
  }
}
