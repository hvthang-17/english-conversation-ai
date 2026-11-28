package vn.englishconversation.config.features;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.features")
public record FeatureProperties(Stage stage) {
  public enum Stage { BOOTSTRAP, ACCOUNT, CATALOG, MATERIALS, LISTENING, PRONUNCIATION, FULL }
}
