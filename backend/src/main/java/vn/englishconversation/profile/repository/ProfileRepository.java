package vn.englishconversation.profile.repository;

import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.englishconversation.profile.entity.LearnerProfile;

@FeatureEnabled(FeatureEnabled.Feature.CORE)
public interface ProfileRepository extends JpaRepository<LearnerProfile, Long> {}
