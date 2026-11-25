package vn.englishconversation.system;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.englishconversation.auth.controller.AuthController;
import vn.englishconversation.auth.service.AccountRateLimiter;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.config.SecurityConfig;
import vn.englishconversation.scenario.controller.CatalogController;
import vn.englishconversation.scenario.dto.ScenarioPage;
import vn.englishconversation.scenario.dto.ScenarioSummary;
import vn.englishconversation.scenario.service.CatalogService;
import vn.englishconversation.user.repository.UserRepository;

@WebMvcTest(controllers = {AuthController.class, CatalogController.class},
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class PhaseZeroApiTest {
  @Autowired MockMvc mvc;
  @MockitoBean AccountService accounts;
  @MockitoBean AccountRateLimiter rateLimiter;
  @MockitoBean UserRepository users;
  @MockitoBean CatalogService catalog;

  @Test
  void acquiresCsrfAndRejectsRequestsBeforeServiceInvocation() throws Exception {
    mvc.perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors.email").exists());
    verifyNoInteractions(accounts);
  }

  @Test
  void preservesFirstAndEndPageParametersInTheHttpContract() throws Exception {
    var sample = new ScenarioSummary("1", "GREETING", "1", "Chào hỏi", "Giới thiệu", "Mẫu thử", List.of("A2"));
    when(catalog.list("A2", null, 0, 2)).thenReturn(new ScenarioPage(List.of(sample), 0, 2, 1));
    when(catalog.list("A2", null, 1, 2)).thenReturn(new ScenarioPage(List.of(), 1, 2, 1));
    for (int page : List.of(0, 1)) {
      mvc.perform(get("/api/v1/scenarios").with(user("fixture"))
              .param("level", "A2").param("page", Integer.toString(page)).param("size", "2"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(page))
          .andExpect(jsonPath("$.size").value(2)).andExpect(jsonPath("$.totalElements").value(1))
          .andExpect(jsonPath("$.content.length()").value(page == 0 ? 1 : 0));
    }
  }
}
