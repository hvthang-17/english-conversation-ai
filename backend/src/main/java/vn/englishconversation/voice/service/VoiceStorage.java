package vn.englishconversation.voice.service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.voice.repository.VoiceStorageRepository;

@Component
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class VoiceStorage {
  private final VoiceStorageRepository persistence;
  private static final Logger log = LoggerFactory.getLogger(VoiceStorage.class);
  private final Path root;
  private final TransactionTemplate tx;
  private final int rawSeconds;
  private final int draftSeconds;
  private final boolean conversationScoringSchema;

  public VoiceStorage(VoiceStorageRepository persistence,
      @Value("${app.voice.temp-directory:.runtime/voice}") String directory,
      @Value("${app.voice.raw-ttl-seconds:3600}") int rawSeconds,
      @Value("${app.voice.draft-ttl-seconds:1800}") int draftSeconds,
      PlatformTransactionManager manager, vn.englishconversation.config.features.FeatureSet features) {
    this.persistence = persistence;
    this.root = Path.of(directory).toAbsolutePath().normalize();
    this.tx = new TransactionTemplate(manager);
    if (rawSeconds < 1 || rawSeconds > 3600 || draftSeconds < 1 || draftSeconds > 1800)
      throw new IllegalArgumentException("Voice TTLs exceed the approved data policy.");
    this.rawSeconds = rawSeconds;
    this.draftSeconds = draftSeconds;
    this.conversationScoringSchema = features.enabled(vn.englishconversation.config.features.FeatureEnabled.Feature.CONVERSATION);
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
      persistence.markFileDeleted(key);
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
    persistence.expireDrafts();
    for (var row : persistence.cleanupCandidates(conversationScoringSchema)) {
      tx.executeWithoutResult(ignored -> clean(row.id()));
    }
    sweepOrphans();
  }

  private void clean(long id) {
    var row = persistence.lockCleanupAudio(conversationScoringSchema, id);
    if (row.isEmpty()) return;
    var audio = row.get();
    String key = audio.storageKey();
    boolean retain = java.util.Set.of("TRANSCRIBING", "READY").contains(audio.status())
        || "CONFIRMED".equals(audio.status()) && audio.preparing();
    if (key != null && (!retain || audio.expired()))
      release(key);
    // Keep the parent until the scoring worker has removed its copy and acknowledged cleanup.
    if (conversationScoringSchema && audio.status().equals("DELETION_PENDING")
        && persistence.scoringCopyCount(id) > 0) return;
    persistence.deleteDetachedAudio(id);
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
            && persistence.referenceCount(key) == 0)
          deleteFile(file);
      }
    } catch (IOException failure) {
      log.warn("Temporary practice audio directory could not be scanned.");
    }
  }
}
