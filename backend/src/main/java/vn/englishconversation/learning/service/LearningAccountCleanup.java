package vn.englishconversation.learning.service;

import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Component;
import vn.englishconversation.learning.repository.LearningCleanupRepository;
import vn.englishconversation.conversation.service.ConversationHistoryService;
import vn.englishconversation.voice.service.VoiceStorage;

@Component
@FeatureEnabled(FeatureEnabled.Feature.LEARNING)
public class LearningAccountCleanup {
  private final LearningCleanupRepository persistence;
  private final ConversationHistoryService learning;
  private final vn.englishconversation.review.repository.ReviewRepository reviews;
  private final VoiceStorage voice;

  public LearningAccountCleanup(LearningCleanupRepository persistence, ConversationHistoryService learning, VoiceStorage voice,
      vn.englishconversation.review.repository.ReviewRepository reviews) {
    this.reviews = reviews;
    this.persistence = persistence;
    this.learning = learning;
    this.voice = voice;
  }

  // Called with the account locked by the deletion worker.
  public void remove(long user) {
    learning.clearLocked(user);
    reviews.clear(user);
    // Preserve references until every file is gone; a failure rolls DB changes back.
    for (var audio :
        persistence.pendingAudio(user)) voice.release(audio.storageKey());
    if (persistence.pendingAudioCount(user)
        > 0) throw new IllegalStateException("Audio cleanup pending");
    persistence.deleteAudio(user);
    persistence.deleteAttempts(user);
    persistence.deleteRequests(user);
  }
}
