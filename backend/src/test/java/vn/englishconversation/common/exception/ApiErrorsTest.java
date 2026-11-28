package vn.englishconversation.common.exception;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class ApiErrorsTest {
  @RestController
  static class ConflictEndpoint {
    @GetMapping("/conflict")
    void conflict() {
      throw new DataIntegrityViolationException("private SQL and constraint details");
    }
  }

  @Test
  void nonAccountIntegrityFailureReturnsNeutralConflictWithoutPersistenceDetails() throws Exception {
    var mvc = MockMvcBuilders.standaloneSetup(new ConflictEndpoint())
        .setControllerAdvice(new ApiErrors()).build();
    var body = mvc.perform(get("/conflict"))
        .andExpect(status().isConflict())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andReturn().getResponse().getContentAsString();
    assertFalse(body.contains("private SQL"));
    assertFalse(body.contains("ACCOUNT_CONFLICT"));
  }
}
