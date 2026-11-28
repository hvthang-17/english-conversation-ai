package vn.englishconversation.config.features;

import jakarta.servlet.http.HttpServletRequest;
import static vn.englishconversation.config.features.FeatureEnabled.Feature;

public final class DisabledFeatureRoutes {
  private DisabledFeatureRoutes() {}
  public static boolean matches(HttpServletRequest request, FeatureSet features) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    Feature required = null;
    if (path.startsWith("/api/v1/admin/listening-exercises")) required = Feature.LISTENING;
    else if (path.startsWith("/api/v1/admin/") || path.startsWith("/api/v1/practice-sessions")
        || path.matches("/api/v1/learning/(cards|stats|recommendations|plans|progress)(/.*)?")) required = Feature.FULL;
    else if (path.startsWith("/api/v1/learning/history") && !request.getMethod().equals("DELETE")) required = Feature.FULL;
    else if (path.startsWith("/api/v1/learning/notebook") || path.matches("/api/v1/lessons/[^/]+/materials")) required = Feature.MATERIALS;
    else if (path.startsWith("/api/v1/listening/") || path.matches("/api/v1/lessons/[^/]+/listening")) required = Feature.LISTENING;
    else if (path.startsWith("/api/v1/pronunciation/")) required = Feature.PRONUNCIATION;
    else if (path.startsWith("/api/v1/lessons")) required = Feature.CATALOG;
    else if (path.startsWith("/api/v1/support/")) required = Feature.FULL;
    return required != null && !features.enabled(required);
  }
}
