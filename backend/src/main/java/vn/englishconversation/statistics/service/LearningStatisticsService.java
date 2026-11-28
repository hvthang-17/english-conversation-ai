package vn.englishconversation.statistics.service;
import org.springframework.stereotype.Service;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.learning.dto.LearningData.Statistics;
import vn.englishconversation.statistics.repository.LearningStatisticsRepository;
@Service
@FeatureEnabled(FeatureEnabled.Feature.LEARNING)
public class LearningStatisticsService {
 private final LearningStatisticsRepository repository;
 public LearningStatisticsService(LearningStatisticsRepository repository) { this.repository=repository; }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Statistics statistics(AccountPrincipal owner) { return repository.statistics(owner.id()); }
}
