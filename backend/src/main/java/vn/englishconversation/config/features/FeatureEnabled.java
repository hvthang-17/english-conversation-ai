package vn.englishconversation.config.features;

import java.lang.annotation.*;
import org.springframework.context.annotation.Conditional;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(FeatureCondition.class)
public @interface FeatureEnabled {
  Feature value();
  enum Feature { BOOTSTRAP, CORE, ACCOUNT_ONLY, CATALOG, MATERIALS, LISTENING, PRONUNCIATION, FULL, CONVERSATION, FEEDBACK, PLANS, PROGRESS, SUPPORT, ADMIN, LEARNING, SPACED_REVIEW, IN_APP_REMINDERS, FOUNDATION }
}
