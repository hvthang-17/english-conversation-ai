package vn.englishconversation.auth.service;

import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.englishconversation.auth.security.TokenCipher;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.auth.repository.AccountRateRepository;

@Service
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class AccountRateLimiter {
  private final AccountRateRepository persistence;
  private final TransactionTemplate rateTx;

  public AccountRateLimiter(AccountRateRepository persistence, PlatformTransactionManager manager) {
    this.persistence = persistence;
    rateTx = new TransactionTemplate(manager);
    rateTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public void check(String key, int limit, int cooldownSeconds) {
    boolean accepted =
        Boolean.TRUE.equals(
            rateTx.execute(
                s -> {
                  String hash = TokenCipher.hash(key);
                  persistence.ensureBucket(hash);
                  var row =
                      persistence.lockBucket(hash)
                          .orElseThrow();
                  if (row.age() >= 3600)
                    persistence.resetWindow(hash);
                  if ((row.age() < 3600 && row.requestCount() >= limit)
                      || row.gap() < cooldownSeconds) return false;
                  persistence.recordRequest(hash);
                  return true;
                }));
    if (!accepted) throw new ApiException(429, "RATE_LIMITED", "Vui lòng chờ trước khi thử lại.");
  }
}
