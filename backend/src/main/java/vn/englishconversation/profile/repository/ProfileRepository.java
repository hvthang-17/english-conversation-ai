package vn.englishconversation.profile.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.englishconversation.profile.entity.LearnerProfile;

@Profile("!bootstrap")
public interface ProfileRepository extends JpaRepository<LearnerProfile, Long> {}
