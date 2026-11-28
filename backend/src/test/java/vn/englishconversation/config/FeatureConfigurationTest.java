package vn.englishconversation.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.englishconversation.config.features.*;

class FeatureConfigurationTest {
  @Test void providerValidationIsFailFastButDisabledProvidersNeedNoSecrets() {
    var runner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
        .withPropertyValues("app.features.stage=ACCOUNT")
        .withUserConfiguration(FeatureConfiguration.class)
        .withInitializer(context -> context.getEnvironment().setActiveProfiles("g1"));
    runner.run(context -> assertNull(context.getStartupFailure()));
    runner.withPropertyValues("app.gemini.enabled=true").run(context -> assertNotNull(context.getStartupFailure()));
    runner.withPropertyValues("app.mail.reminders-enabled=true").run(context -> assertNotNull(context.getStartupFailure()));
  }
  @Test void invalidStagesFailBeforeBusinessBeansAreCreated() {
    for (String[] profiles : new String[][] {{"g1","g6"},{"bootstrap","g6"},{"production"},{"unknown"},{"g6","p0-db"}}) {
      var env = new MockEnvironment(); env.setActiveProfiles(profiles);
      assertThrows(IllegalStateException.class, () -> FeatureSet.resolve(env));
    }
    var mismatch = new MockEnvironment().withProperty("app.features.stage", "FULL");
    mismatch.setActiveProfiles("g1");
    assertThrows(IllegalStateException.class, () -> FeatureSet.resolve(mismatch));
  }
  @Test void explicitSelectionAndLegacyAdaptersAgree() {
    var env = new MockEnvironment().withProperty("app.features.stage", "FULL");
    env.setActiveProfiles("g6", "production");
    assertTrue(FeatureSet.resolve(env).full());
    env.setActiveProfiles("bootstrap", "p0-db"); env.setProperty("app.features.stage", "BOOTSTRAP");
    assertEquals(FeatureProperties.Stage.BOOTSTRAP, FeatureSet.resolve(env).stage());
    assertEquals(FeatureProperties.Stage.BOOTSTRAP, FeatureSet.resolve(new MockEnvironment()).stage());
  }
  @Test void disabledProvidersNeedNoKeysAndDisabledRoutesCannotReachBroadMatchers() {
    var settings = new vn.englishconversation.ai.config.ChatSettings(new MockEnvironment());
    assertFalse(settings.enabled);
    var client = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
    var provider = new vn.englishconversation.ai.provider.GeminiTextProvider(settings, client,
        java.net.URI.create("http://127.0.0.1:1/fixture"));
    assertFalse(provider.ready());
    assertEquals("AI_NOT_CONFIGURED", provider.generate(
        new vn.englishconversation.ai.provider.TextProvider.Prompt("fixture", java.util.List.of(), 20)).errorCode());
    org.mockito.Mockito.verifyNoInteractions(client);
    var env = new MockEnvironment(); env.setActiveProfiles("g1");
    var features = FeatureSet.resolve(env);
    for (String path : new String[] {"/api/v1/practice-sessions/1", "/api/v1/learning/cards", "/api/v1/learning/notebook", "/api/v1/listening/exercises", "/api/v1/admin/accounts"})
      assertTrue(DisabledFeatureRoutes.matches(new MockHttpServletRequest("GET",path),features),path);
    assertFalse(DisabledFeatureRoutes.matches(new MockHttpServletRequest("DELETE","/api/v1/learning/data"),features));
    assertFalse(DisabledFeatureRoutes.matches(new MockHttpServletRequest("GET","/api/v1/auth/csrf"),features));
  }
}
