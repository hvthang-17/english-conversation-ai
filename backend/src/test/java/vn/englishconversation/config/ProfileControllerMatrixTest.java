package vn.englishconversation.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import vn.englishconversation.learning.controller.*;
import vn.englishconversation.conversation.controller.PracticeController;
import vn.englishconversation.listening.controller.ListeningController;
import vn.englishconversation.pronunciation.controller.PronunciationController;
import vn.englishconversation.admin.controller.MaterialAdminController;

class ProfileControllerMatrixTest {
  @Test
  void actualControllerConditionsPreserveStageAdaptersAndExposeProductionDifference() {
    var controllers = List.of(LearningController.class, FoundationLearningDataController.class,
        NotebookController.class, PracticeController.class, ListeningController.class,
        PronunciationController.class, MaterialAdminController.class, vn.englishconversation.system.controller.HealthController.class);
    for (String profile : List.of("bootstrap", "g1", "g2", "g3", "g4", "g5", "g6")) {
      try (var context = new AnnotationConfigApplicationContext()) {
        context.getEnvironment().setActiveProfiles(profile);
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
            "fixture-stage", java.util.Map.of("app.features.stage",
                vn.englishconversation.config.features.FeatureProperties.Stage.values()[
                    List.of("bootstrap","g1","g2","g3","g4","g5","g6").indexOf(profile)].name())));
        context.getBeanFactory().registerSingleton(vn.englishconversation.config.features.FeatureSet.class.getName(),
            vn.englishconversation.config.features.FeatureSet.resolve(context.getEnvironment()));
        // Real controller definitions/conditions with fixture service collaborators; no DB/provider.
        for (var controller : controllers) {
          var condition = controller.getAnnotation(vn.englishconversation.config.features.FeatureEnabled.class);
          boolean enabled = condition == null || vn.englishconversation.config.features.FeatureSet.resolve(context.getEnvironment()).enabled(condition.value());
          if (!enabled) { context.register(controller); continue; }
          for (var type : controller.getConstructors()[0].getParameterTypes()) {
            if (type == java.util.Optional.class) continue;
            if (!context.getBeanFactory().containsSingleton(type.getName()))
              context.getBeanFactory().registerSingleton(type.getName(), mock(type));
          }
          context.register(controller);
        }
        context.refresh();
        if (profile.equals("g1"))
          assertFalse(context.containsBean(vn.englishconversation.learning.service.NotebookService.class.getName()));
        boolean full = profile.equals("g6") || profile.equals("production");
        assertEquals(full, !context.getBeansOfType(LearningController.class).isEmpty(), profile);
        assertEquals(full, !context.getBeansOfType(PracticeController.class).isEmpty(), profile);
        assertEquals(profile.matches("g[1-5]"), !context.getBeansOfType(FoundationLearningDataController.class).isEmpty(), profile);
        assertEquals(!List.of("bootstrap", "g1", "g2").contains(profile), !context.getBeansOfType(NotebookController.class).isEmpty(), profile);
        assertEquals(List.of("g4", "g5", "g6").contains(profile), !context.getBeansOfType(ListeningController.class).isEmpty(), profile);
        assertEquals(List.of("g5", "g6").contains(profile), !context.getBeansOfType(PronunciationController.class).isEmpty(), profile);
        assertEquals(profile.equals("g6"), !context.getBeansOfType(MaterialAdminController.class).isEmpty(), profile);
        var reported = context.getBean(vn.englishconversation.system.controller.HealthController.class).features();
        assertEquals(full, reported.learningWorkspaceAvailable(), profile);
        assertEquals(!List.of("bootstrap","g1","g2").contains(profile), reported.lessonMaterialsAvailable(), profile);
      }
    }
  }
}
