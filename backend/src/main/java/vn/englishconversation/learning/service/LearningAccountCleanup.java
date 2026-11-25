package vn.englishconversation.learning.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.learning.repository.LearningStore;
import vn.englishconversation.voice.service.VoiceStorage;

@Component
@Profile("g6 | (!bootstrap & !g1 & !g2 & !g3 & !g4 & !g5)")
public class LearningAccountCleanup {
  private final JdbcStore db;
  private final LearningStore learning;
  private final VoiceStorage voice;

  public LearningAccountCleanup(JdbcStore db, LearningStore learning, VoiceStorage voice) {
    this.db = db;
    this.learning = learning;
    this.voice = voice;
  }

  // Called with the account locked by the deletion worker.
  public void remove(long user) {
    for (long session : learning.lockAllSessions(user)) learning.deletePractice(user, session);
    learning.deleteCards(user);
    // Preserve references until every file is gone; a failure rolls DB changes back.
    for (var audio :
        db.rows(
            "SELECT a.storage_key FROM audio_submissions a JOIN ai_requests r ON"
                + " r.id=a.ai_request_id WHERE r.user_id=? AND a.storage_key IS NOT NULL",
            user)) voice.release(audio.text("storage_key"));
    if (db.count(
            "SELECT COUNT(*) FROM audio_submissions a JOIN ai_requests r ON r.id=a.ai_request_id"
                + " WHERE r.user_id=? AND a.storage_key IS NOT NULL",
            user)
        > 0) throw new IllegalStateException("Audio cleanup pending");
    db.update(
        "DELETE a FROM audio_submissions a JOIN ai_requests r ON r.id=a.ai_request_id WHERE"
            + " r.user_id=?",
        user);
    db.update(
        "DELETE a FROM ai_attempts a JOIN ai_requests r ON r.id=a.ai_request_id WHERE"
            + " r.user_id=?",
        user);
    db.update("DELETE FROM ai_requests WHERE user_id=?", user);
  }
}
