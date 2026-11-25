package vn.englishconversation.pronunciation.service;

import java.nio.file.Path;
import vn.englishconversation.pronunciation.dto.PronunciationData.Assessment;

public interface PronunciationProvider {
  boolean available();

  Assessment assess(Path wav, String reference, String locale);

  final class Failure extends RuntimeException {
    private final boolean retryable;
    private final boolean unknown;

    public Failure(String code, boolean retryable, boolean unknown) {
      super(code);
      this.retryable = retryable;
      this.unknown = unknown;
    }

    public boolean retryable() { return retryable; }
    public boolean unknown() { return unknown; }
  }
}
