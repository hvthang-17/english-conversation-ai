package vn.englishconversation.pronunciation.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Value;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Component;

@Component
@FeatureEnabled(FeatureEnabled.Feature.PRONUNCIATION)
public class PronunciationAudio {
  private final Path directory;

  public PronunciationAudio(
      @Value("${app.pronunciation.audio-directory:../.runtime/pronunciation}") String directory) {
    this.directory = Path.of(directory).toAbsolutePath().normalize();
  }

  public String save(byte[] wav) {
    var key = UUID.randomUUID().toString();
    try {
      Files.createDirectories(directory);
      Files.write(path(key), wav, StandardOpenOption.CREATE_NEW);
      return key;
    } catch (IOException failure) {
      remove(key);
      throw new IllegalStateException("Pronunciation audio not writable");
    }
  }

  public Path path(String key) {
    if (key == null || !UUID.fromString(key).toString().equals(key))
      throw new IllegalArgumentException("Invalid audio storage key");
    return directory.resolve(key + ".wav");
  }

  public boolean remove(String key) {
    try {
      Files.deleteIfExists(path(key));
      return true;
    } catch (IOException failure) {
      return false;
    }
  }

  public void removeOrphans(Instant cutoff, Predicate<String> referenced) {
    if (!Files.isDirectory(directory)) return;
    try (var paths = Files.newDirectoryStream(directory, "*.wav")) {
      for (var file : paths) {
        var name = file.getFileName().toString();
        var key = name.substring(0, name.length() - 4);
        if (key.matches("[0-9a-f-]{36}") && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
            && Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)
            && !referenced.test(key)) remove(key);
      }
    } catch (IOException ignored) {
      // Sweep again on the next tick.
    }
  }
}
