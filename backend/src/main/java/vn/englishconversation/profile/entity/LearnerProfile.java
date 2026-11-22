package vn.englishconversation.profile.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "learner_profiles")
public class LearnerProfile {
  @Id public Long userId;

  @Column(nullable = false, length = 100)
  public String displayName;

  @Column(nullable = false, length = 2)
  public String selectedLevel;

  @Column(length = 1000)
  public String learningGoal;

  public Short dailyGoalMinutes;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant updatedAt;
}
