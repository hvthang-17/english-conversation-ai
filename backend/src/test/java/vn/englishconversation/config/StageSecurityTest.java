package vn.englishconversation.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.englishconversation.scenario.controller.FoundationTopicsController;

@WebMvcTest(controllers=FoundationTopicsController.class, excludeAutoConfiguration=UserDetailsServiceAutoConfiguration.class,
    properties="app.features.stage=ACCOUNT")
@Import(SecurityConfig.class)
@ActiveProfiles("g1")
class StageSecurityTest {
  @Autowired MockMvc mvc;
  @MockitoBean vn.englishconversation.user.repository.UserRepository users;
  @MockitoBean vn.englishconversation.scenario.repository.CatalogRepository catalog;
  @Test void stageRoutesRemainProtectedAndDisabledFeaturesAreDenied() throws Exception {
    mvc.perform(get("/api/v1/topics")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/topics").with(user("learner").roles("LEARNER"))).andExpect(status().isOk());
    mvc.perform(get("/api/v1/practice-sessions/1").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/learning/notebook").with(user("learner").roles("LEARNER"))).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/auth/password").with(user("learner").roles("LEARNER")))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
  }
}
