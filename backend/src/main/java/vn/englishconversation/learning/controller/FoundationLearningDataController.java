package vn.englishconversation.learning.controller;

import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.common.exception.ApiException;

@RestController
@RequestMapping("/api/v1/learning")
@FeatureEnabled(FeatureEnabled.Feature.FOUNDATION)
public class FoundationLearningDataController {
    private final vn.englishconversation.learning.service.FoundationLearningCleanupService cleanup;

    public FoundationLearningDataController(
            vn.englishconversation.learning.service.FoundationLearningCleanupService cleanup) {
        this.cleanup = cleanup;
    }

    @DeleteMapping("/data")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteData(@AuthenticationPrincipal AccountPrincipal owner) {
        cleanup.deleteData(owner);
    }

    @DeleteMapping("/history/{id}")
    public void deleteSession(@PathVariable long id) {
        throw new ApiException(404, "SESSION_NOT_FOUND", "Không tìm thấy buổi luyện tập.");
    }
}
