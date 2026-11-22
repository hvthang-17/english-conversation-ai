package vn.englishconversation.ai.config;

import java.math.BigDecimal;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("!bootstrap")
public class ChatSettings {
  public final boolean enabled;
  public final String apiKey, model, fingerprintSecret, pricingReference;
  public final int timeoutSeconds,
      leaseSeconds,
      maxAttempts,
      inputLimit,
      outputLimit,
      contextLimit,
      maxOutputTokens,
      userRpm,
      globalRpm,
      globalConcurrent,
      totalAttemptLimit;
  public final BigDecimal inputPrice, outputPrice;

  public ChatSettings(Environment e) {
    enabled = e.getProperty("app.gemini.enabled", Boolean.class, false);
    apiKey = e.getProperty("GEMINI_API_KEY", "");
    model = e.getProperty("GEMINI_CHAT_MODEL", "");
    fingerprintSecret = e.getProperty("CHAT_FINGERPRINT_SECRET", "");
    timeoutSeconds = number(e, "timeout-seconds", 30, 1, 60);
    leaseSeconds = timeoutSeconds + 30;
    maxAttempts = number(e, "max-attempts", 2, 1, 2);
    inputLimit = number(e, "input-limit", 1000, 1, 1000);
    outputLimit = number(e, "output-limit", 2000, 1, 4000);
    contextLimit = number(e, "context-limit", 80000, 1000, 100000);
    maxOutputTokens = number(e, "max-output-tokens", 512, 64, 1024);
    userRpm = number(e, "user-rpm", 6, 1, 60);
    globalRpm = number(e, "global-rpm", 10, 1, 60);
    globalConcurrent = number(e, "global-concurrent", 2, 1, 4);
    totalAttemptLimit = number(e, "total-attempt-limit", 0, 0, 1000000);
    pricingReference = e.getProperty("app.chat.pricing-reference", "");
    inputPrice = e.getProperty("app.chat.input-usd-per-million", BigDecimal.class);
    outputPrice = e.getProperty("app.chat.output-usd-per-million", BigDecimal.class);
    if (enabled && (!model.matches("[a-zA-Z0-9._-]{1,120}") || apiKey.isBlank()))
      throw new IllegalStateException(
          "Gemini enabled but model/key configuration is missing or invalid");
  }

  private int number(Environment e, String name, int fallback, int min, int max) {
    int v = e.getProperty("app.chat." + name, Integer.class, fallback);
    if (v < min || v > max) throw new IllegalStateException("Invalid chat setting: " + name);
    return v;
  }

  public boolean fingerprintsReady() {
    return fingerprintSecret.length() >= 32;
  }
}
