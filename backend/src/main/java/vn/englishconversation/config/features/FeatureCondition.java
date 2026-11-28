package vn.englishconversation.config.features;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class FeatureCondition implements Condition {
  @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    var attributes = metadata.getAnnotationAttributes(FeatureEnabled.class.getName());
    return FeatureSet.resolve(context.getEnvironment())
        .enabled((FeatureEnabled.Feature) attributes.get("value"));
  }
}
