package vn.englishconversation.voice.service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.common.repository.JdbcStore;

@Component
@Profile("g6 | (!bootstrap & !g1 & !g2 & !g3 & !g4 & !g5)")
public class VoiceStorage {
  private static final Logger log = LoggerFactory.getLogger(VoiceStorage.class);
  private final Path root;
  private final JdbcStore db;
  private final TransactionTemplate tx;
  private final int rawSeconds;
  private final int draftSeconds;
  private final boolean conversationScoringSchema;

  public VoiceStorage(
      @Value("${app.voice.temp-directory:.runtime/voice}") String directory,
      @Value("${app.voice.raw-ttl-seconds:3600}") int rawSeconds,
      @Value("${app.voice.draft-ttl-seconds:1800}") int draftSeconds,
      JdbcStore db,
      PlatformTransactionManager manager, Environment environment) {
    this.root = Path.of(directory).toAbsolutePath().normalize();
    this.db = db;
    this.tx = new TransactionTemplate(manager);
    if (rawSeconds < 1 || rawSeconds > 3600 || draftSeconds < 1 || draftSeconds > 1800)
      throw new IllegalArgumentException("Voice TTLs exceed the approved data policy.");
    this.rawSeconds = rawSeconds;
    this.draftSeconds = draftSeconds;
    this.conversationScoringSchema = environment.acceptsProfiles(Profiles.of("g6"));
  }

  public int rawSeconds() { return rawSeconds; }

  public int draftSeconds() { return draftSeconds; }

  public Path path(String key) {
    return root.resolve(UUID.fromString(key).toString() + ".wav");
  }

  public void stage(String key, byte[] audio) {
    try {
      Files.createDirectories(root);
      Files.write(path(key), audio, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    } catch (IOException failure) {
      throw new ApiException(503, "AUDIO_STORAGE_UNAVAILABLE", "Chưa thể lưu tạm bản ghi.");
    }
  }

  public void release(String key) {
    if (deleteFile(path(key)))
      db.update(
          "UPDATE audio_submissions SET storage_key=NULL,audio_deleted_at=UTC_TIMESTAMP(6) WHERE storage_key=?",
          key);
  }

  private boolean deleteFile(Path file) {
    try {
      Files.deleteIfExists(file);
      return true;
    } catch (IOException failure) {
      log.warn("Temporary practice audio could not be removed; scheduled cleanup will retry.");
      return false;
    }
  }

  @Scheduled(fixedDelayString = "${app.voice.cleanup-delay-ms:300000}")
  public void sweep() {
    db.update(
        """
        UPDATE audio_submissions SET status='EXPIRED',draft_transcript=NULL,transcript_expires_at=NULL
        WHERE status='READY' AND transcript_expires_at<=UTC_TIMESTAMP(6)
        """);
    for (var row : db.rows(
        """
        SELECT id FROM audio_submissions WHERE status='DELETION_PENDING'
        OR storage_key IS NOT NULL AND (status NOT IN ('TRANSCRIBING','READY') OR expires_at<=UTC_TIMESTAMP(6))
        ORDER BY id LIMIT 200
        """)) {
      tx.executeWithoutResult(ignored -> clean(row.number("id")));
    }
    sweepOrphans();
  }

  private void clean(long id) {
    var row = db.find(
        """
        SELECT status,storage_key,expires_at<=UTC_TIMESTAMP(6) AS expired
        FROM audio_submissions WHERE id=? FOR UPDATE
        """, id);
    if (row.isEmpty()) return;
    var audio = row.get();
    String key = audio.text("storage_key");
    if (key != null && (!java.util.Set.of("TRANSCRIBING", "READY").contains(audio.text("status")) || audio.flag("expired")))
      release(key);
    // Keep the parent until the scoring worker has removed its copy and acknowledged cleanup.
    if (conversationScoringSchema && audio.text("status").equals("DELETION_PENDING")
        && db.count("""
            SELECT COUNT(*) FROM pronunciation_attempts
            WHERE audio_submission_id=? AND audio_storage_key IS NOT NULL
            """, id) > 0) return;
    db.update(
        """
        DELETE FROM audio_submissions
        WHERE id=? AND session_id IS NULL AND status='DELETION_PENDING' AND storage_key IS NULL
        """, id);
  }

  private void sweepOrphans() {
    if (!Files.isDirectory(root)) return;
    try (var files = Files.newDirectoryStream(root, "*.wav")) {
      Instant cutoff = Instant.now().minusSeconds(rawSeconds);
      for (Path file : files) {
        String name = file.getFileName().toString();
        if (!name.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.wav")) continue;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
        String key = name.substring(0, 36);
        if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)
            && db.count("SELECT COUNT(*) FROM audio_submissions WHERE storage_key=?", key) == 0)
          deleteFile(file);
      }
    } catch (IOException failure) {
      log.warn("Temporary practice audio directory could not be scanned.");
    }
  }
}
