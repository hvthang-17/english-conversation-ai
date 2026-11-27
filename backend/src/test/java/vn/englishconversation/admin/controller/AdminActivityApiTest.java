package vn.englishconversation.admin.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.englishconversation.admin.dto.AdminActivity;
import vn.englishconversation.admin.dto.AdminPage;
import vn.englishconversation.admin.service.AdminActivityService;
import vn.englishconversation.config.SecurityConfig;
import vn.englishconversation.user.repository.UserRepository;

@WebMvcTest(controllers = AdminActivityController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
@ActiveProfiles("g6")
class AdminActivityApiTest {
  @Autowired MockMvc mvc;
  @MockitoBean AdminActivityService activity;
  @MockitoBean UserRepository users;

  @Test
  void bothEndpointsRequireAdminBeforeServiceInvocation() throws Exception {
    for (String endpoint : List.of("audit", "ai-attempts")) {
      mvc.perform(get("/api/v1/admin/" + endpoint).param("page", "0")).andExpect(status().isUnauthorized());
      mvc.perform(get("/api/v1/admin/" + endpoint).param("page", "0").with(user("learner").roles("LEARNER")))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(activity);
  }

  @Test
  void pagedContractCarriesFiltersAndLegacyContractStaysAnArray() throws Exception {
    var row = new AdminActivity("7", "CHAT", "Gemini", "model", null, "FAILED", "0", "2026-11-26 04:00:00");
    when(activity.list(false, 2, 12, "Gemini", "FAILED")).thenReturn(new AdminPage<>(List.of(row), 2, 12, 31));
    mvc.perform(get("/api/v1/admin/ai-attempts").param("page", "2").param("q", "Gemini").param("status", "FAILED")
        .with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.total").value(31)).andExpect(jsonPath("$.items[0].id").value("7"));
    when(activity.list(true, 0, 50, "", "")).thenReturn(new AdminPage<>(List.of(row), 0, 50, 80));
    mvc.perform(get("/api/v1/admin/audit").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("7"));
    when(activity.list(true, 1, 24, "Tạo chủ đề", "")).thenReturn(new AdminPage<>(List.of(), 1, 24, 25));
    mvc.perform(get("/api/v1/admin/audit").param("page", "1").param("size", "24").param("q", "Tạo chủ đề")
        .with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(25));
  }
}
