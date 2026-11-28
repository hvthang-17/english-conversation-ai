package vn.englishconversation.config.features;

import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
@org.springframework.boot.context.properties.EnableConfigurationProperties(FeatureProperties.class)
public class FeatureConfiguration {
  @Bean public FeatureSet featureSet(Environment environment) {
    var features = FeatureSet.resolve(environment);
    requireProvider(environment, features, "app.gemini.enabled", FeatureEnabled.Feature.FULL, "GEMINI_API_KEY", "GEMINI_CHAT_MODEL");
    requireProvider(environment, features, "app.listening.azure.enabled", FeatureEnabled.Feature.LISTENING, "app.listening.azure.key", "app.listening.azure.region");
    requireProvider(environment, features, "app.pronunciation.azure.enabled", FeatureEnabled.Feature.PRONUNCIATION, "app.pronunciation.azure.key", "app.pronunciation.azure.region");
    requireProvider(environment, features, "app.mail.enabled", FeatureEnabled.Feature.CORE, "app.mail.host", "app.mail.from", "app.mail.outbox-key");
    if (environment.getProperty("app.mail.reminders-enabled", Boolean.class, false)
        && !environment.getProperty("app.mail.enabled", Boolean.class, false))
      throw new IllegalStateException("Mail reminders require mail.enabled");
    return features;
  }
  private static void requireProvider(Environment env, FeatureSet features, String enabled,
      FeatureEnabled.Feature feature, String... required) {
    if (!env.getProperty(enabled, Boolean.class, false)) return;
    if (!features.enabled(feature)) throw new IllegalStateException(enabled + " requires feature " + feature);
    for (String key : required) if (env.getProperty(key, "").isBlank())
      throw new IllegalStateException(enabled + " requires configuration " + key);
  }
}
