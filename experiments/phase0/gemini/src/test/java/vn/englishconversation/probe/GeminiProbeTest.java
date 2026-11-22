package vn.englishconversation.probe;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeminiProbeTest {
    @Test void liveIsRefusedEvenIfAKeyExistsButApprovalIsMissing() {
        assertThrows(IllegalStateException.class, () -> GeminiProbe.checkLivePermission(Map.of("GEMINI_API_KEY", "dummy-not-a-key")));
    }
    @Test void liveNeedsExplicitTierModelsAndRequestLimit() {
        assertThrows(IllegalStateException.class, () -> GeminiProbe.checkLivePermission(Map.of("PHASE0_ALLOW_LIVE", "true", "GEMINI_API_KEY", "dummy")));
    }
    @Test void payloadUsesStatelessBoundedRequestsWithoutAKey() throws Exception {
        for (var test : GeminiProbe.CASES) {
            var request = GeminiProbe.buildRequest(test, "offline-only", new byte[]{1, 2});
            assertFalse(request.path("store").asBoolean());
            assertFalse(request.path("background").asBoolean());
            assertTrue(request.path("generation_config").path("max_output_tokens").asInt() <= 2048);
            assertFalse(request.has("api_key"));
        }
    }
    @Test void feedbackAllowsNoErrorsForCorrectInput() {
        assertTrue(GeminiProbe.validFeedback("""
            {"summary":"Good","strengths":[],"corrections":[],"vocabulary":[],"next_practice":"Continue"}
            """));
    }
    @Test void rejectsInventedSourceAndMalformedOutput() {
        assertFalse(GeminiProbe.validFeedback("not json"));
        assertFalse(GeminiProbe.validFeedback("""
            {"summary":"x","strengths":[],"vocabulary":[],"next_practice":"x",
             "corrections":[{"source_message_id":"other-user","original_text":"x","suggested_text":"y","item_type":"ERROR","category":"GRAMMAR","explanation_vi":"x"}]}
            """));
    }
    @Test void acceptsCorrectionOnlyWhenItsQuoteMatchesTheProvidedSource() {
        assertTrue(GeminiProbe.validFeedback("""
            {"summary":"x","strengths":[],"vocabulary":[],"next_practice":"x",
             "corrections":[{"source_message_id":"u1","original_text":"I want order","suggested_text":"I want to order","item_type":"ERROR","category":"GRAMMAR","explanation_vi":"Thiếu to"}]}
            """));
    }
    @Test void ignoresThoughtAndNonTextContentWhenReadingAnAnswer() throws Exception {
        var response = GeminiProbe.JSON.readTree("""
            {"steps":[{"type":"thought","content":[{"type":"text","text":"hidden"}]},
            {"type":"model_output","content":[{"type":"text","text":"Hello"},{"type":"audio","data":"hidden"}]}]}
            """);
        assertEquals("Hello", GeminiProbe.outputText(response));
    }
    @Test void rejectsOversizedAudioBeforeSending() {
        assertThrows(IllegalArgumentException.class, () -> GeminiProbe.buildRequest(GeminiProbe.CASES.get(3), "offline-only", new byte[2_000_001]));
    }
}
