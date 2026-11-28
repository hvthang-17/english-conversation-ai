package vn.englishconversation.ai.provider;

import java.util.List;
import java.util.Map;

public interface TextProvider {
  boolean ready();

  String model();

  Outcome generate(Prompt prompt);

  record Text(String role, String content) {}

  record Prompt(
      String instruction, List<Text> history, int maxOutputTokens, Map<String, Object> schema,
      byte[] waveAudio, boolean schemaInInstruction) {
    public Prompt(String instruction, List<Text> history, int maxOutputTokens, Map<String, Object> schema,
        byte[] waveAudio) {
      this(instruction, history, maxOutputTokens, schema, waveAudio, false);
    }
    public Prompt(String instruction, List<Text> history, int maxOutputTokens, Map<String, Object> schema) {
      this(instruction, history, maxOutputTokens, schema, null);
    }

    public Prompt(String instruction, List<Text> history, int maxOutputTokens) {
      this(instruction, history, maxOutputTokens, Map.of());
    }
  }

  record Outcome(
      String text,
      String model,
      String providerId,
      Long inputTokens,
      Long outputTokens,
      boolean cacheUsed,
      String errorCode,
      boolean unknown,
      boolean retryable,
      int retryAfterSeconds) {
    public static Outcome failure(String code, boolean unknown, boolean retryable, int seconds) {
      return new Outcome(null, null, null, null, null, false, code, unknown, retryable, seconds);
    }

    public boolean success() {
      return text != null && errorCode == null;
    }
  }
}
