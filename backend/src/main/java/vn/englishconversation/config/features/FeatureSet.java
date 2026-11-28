package vn.englishconversation.config.features;

import java.util.*;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import static vn.englishconversation.config.features.FeatureProperties.Stage;
import static vn.englishconversation.config.features.FeatureEnabled.Feature;

/** Single legacy-profile bridge. Feature decisions never infer availability from provider keys. */
public record FeatureSet(Stage stage) {
  public boolean full() { return stage == Stage.FULL; }
  public boolean enabled(Feature feature) {
    return switch (feature) {
      case BOOTSTRAP -> stage == Stage.BOOTSTRAP;
      case CORE -> stage != Stage.BOOTSTRAP;
      case ACCOUNT_ONLY -> stage == Stage.ACCOUNT;
      case FOUNDATION -> stage != Stage.BOOTSTRAP && !full();
      case CATALOG -> stage.ordinal() >= Stage.CATALOG.ordinal();
      case MATERIALS -> stage.ordinal() >= Stage.MATERIALS.ordinal();
      case LISTENING -> stage.ordinal() >= Stage.LISTENING.ordinal();
      case PRONUNCIATION -> stage.ordinal() >= Stage.PRONUNCIATION.ordinal();
      case FULL, CONVERSATION, FEEDBACK, PLANS, PROGRESS, SUPPORT, ADMIN, LEARNING, SPACED_REVIEW, IN_APP_REMINDERS -> full();
    };
  }
  public static FeatureSet resolve(Environment environment) {
    var profiles = new HashSet<>(Arrays.asList(environment.getActiveProfiles()));
    if (profiles.isEmpty()) profiles.addAll(Arrays.asList(environment.getDefaultProfiles()));
    var names = List.of("bootstrap", "g1", "g2", "g3", "g4", "g5", "g6");
    var selected = names.stream().filter(profiles::contains).toList();
    if (selected.size() > 1) throw new IllegalStateException("Choose exactly one feature stage profile");
    Stage legacy = selected.isEmpty() ? Stage.BOOTSTRAP : Stage.values()[names.indexOf(selected.getFirst())];
    Stage explicit = Binder.get(environment).bind("app.features", FeatureProperties.class)
        .map(FeatureProperties::stage).orElse(null);
    if (explicit != null && (selected.isEmpty() || explicit != legacy))
      throw new IllegalStateException("app.features.stage requires its matching bootstrap/g1-g6 configuration adapter");
    if (profiles.contains("production") && (selected.isEmpty() || legacy == Stage.BOOTSTRAP))
      throw new IllegalStateException("Production requires an explicit business stage profile");
    if (profiles.contains("p0-db") && legacy != Stage.BOOTSTRAP)
      throw new IllegalStateException("p0-db requires bootstrap");
    if (selected.isEmpty() && profiles.stream().anyMatch(p -> !p.equals("default")))
      throw new IllegalStateException("Unknown profile cannot select a business feature stage");
    return new FeatureSet(explicit == null ? legacy : explicit);
  }
}
