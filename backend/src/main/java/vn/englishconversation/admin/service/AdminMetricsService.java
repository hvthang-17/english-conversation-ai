package vn.englishconversation.admin.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.admin.dto.AdminMetrics;
import vn.englishconversation.admin.repository.AdminMetricsStore;
import vn.englishconversation.common.exception.ApiException;

@Service
@FeatureEnabled(FeatureEnabled.Feature.ADMIN)
public class AdminMetricsService {
  private final AdminMetricsStore store;
  private final AdminMetrics.Limits limits;

  public AdminMetricsService(AdminMetricsStore store,
      @Value("${app.chat.global-rpm:10}") int geminiGlobal,
      @Value("${app.chat.user-rpm:6}") int geminiUser,
      @Value("${app.chat.global-concurrent:2}") int concurrent,
      @Value("${app.chat.total-attempt-limit:0}") int geminiTotal,
      @Value("${app.pronunciation.daily-request-limit:6}") int pronunciationUser,
      @Value("${app.pronunciation.provider-daily-limit:6}") int pronunciationProvider) {
    this.store=store;
    limits=new AdminMetrics.Limits(geminiGlobal,geminiUser,concurrent,geminiTotal,pronunciationUser,pronunciationProvider);
  }

  @Transactional(readOnly=true)
  public AdminMetrics metrics(int days) {
    validateDays(days);
    Instant until=Instant.now().truncatedTo(ChronoUnit.MICROS);
    Instant from=until.minus(days,ChronoUnit.DAYS);
    var start=LocalDateTime.ofInstant(from,ZoneOffset.UTC);
    var end=LocalDateTime.ofInstant(until,ZoneOffset.UTC);
    return new AdminMetrics(from,until,store.accounts(start,end),store.learning(start,end),store.catalog(),store.popularContent(start,end),
        store.ai(start,end),limits);
  }

  public static void validateDays(int days) {
    if (days<1 || days>90) throw new ApiException(400,"INVALID_DAYS","Chọn kỳ từ 1 đến 90 ngày.");
  }
}
