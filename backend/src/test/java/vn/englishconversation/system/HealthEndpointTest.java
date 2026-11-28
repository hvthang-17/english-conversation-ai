package vn.englishconversation.system;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties="app.features.stage=BOOTSTRAP")
@AutoConfigureMockMvc
@ActiveProfiles("bootstrap")
class HealthEndpointTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void healthIsPublicAndReturnsProcessStatus() throws Exception {
    mockMvc
        .perform(get("/api/v1/system/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.application").value("english-conversation-ai"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }

  @Test
  void businessPathsAreClosedUntilAuthenticationIsImplemented() throws Exception {
    mockMvc.perform(get("/api/v1/conversations")).andExpect(status().isUnauthorized());
  }

  @Test
  void publicHealthDoesNotOpenOtherHttpMethods() throws Exception {
    mockMvc
        .perform(post("/api/v1/system/health").with(csrf()))
        .andExpect(status().isUnauthorized());
  }
}
