package vn.englishconversation.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Isolated bounded probe; default mode never constructs a network request. */
public final class GeminiProbe {
    static final ObjectMapper JSON = new ObjectMapper();
    static final URI ENDPOINT = URI.create("https://generativelanguage.googleapis.com/v1beta/interactions");
    static final String AUDIO_TEXT = "I would like a bowl of vegetable soup and a glass of water, please.";
    static final Map<String, String> SOURCE_MESSAGES = Map.of(
            "u1", "I want order a chicken soup.",
            "u2", "I am allergic to peanuts.",
            "u3", "I would like some water, please.");

    record Case(String id, String task, String modelVariable, int maxOutputTokens) {}
    static final List<Case> CASES = List.of(
            new Case("CHAT_A2", "CHAT", "GEMINI_CHAT_MODEL", 512),
            new Case("CHAT_B1", "CHAT", "GEMINI_CHAT_MODEL", 512),
            new Case("FEEDBACK", "FEEDBACK", "GEMINI_FEEDBACK_MODEL", 2048),
            new Case("TRANSCRIPTION", "TRANSCRIPTION", "GEMINI_AUDIO_MODEL", 512));

    private GeminiProbe() {}

    static void checkLivePermission(Map<String, String> env) {
        if (!"true".equals(env.get("PHASE0_ALLOW_LIVE"))) {
            throw new IllegalStateException("Live calls disabled; obtain explicit approval before enabling.");
        }
        if (env.getOrDefault("GEMINI_API_KEY", "").isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY missing; never paste it into chat or logs.");
        }
        String tier = env.getOrDefault("PHASE0_API_TIER", "");
        if (!List.of("FREE", "PAID").contains(tier) || !"4".equals(env.get("PHASE0_MAX_CALLS"))) {
            throw new IllegalStateException("Explicit tier and PHASE0_MAX_CALLS=4 required.");
        }
        if ("PAID".equals(tier)) {
            try {
                if (new java.math.BigDecimal(env.getOrDefault("PHASE0_BUDGET_USD", "0")).signum() <= 0) {
                    throw new IllegalStateException("Approved paid budget must be positive.");
                }
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("Invalid paid budget; values are not printed.");
            }
        }
        for (Case test : CASES) {
            String model = env.getOrDefault(test.modelVariable(), "");
            if (!model.matches("[a-z0-9][a-z0-9._-]{1,100}")) {
                throw new IllegalStateException("Explicit valid model IDs are required before live calls.");
            }
        }
    }

    static ObjectNode buildRequest(Case test, String model, byte[] syntheticWav) throws Exception {
        ObjectNode request = JSON.createObjectNode();
        request.put("model", model);
        request.put("store", false);
        request.put("background", false);
        request.putObject("generation_config").put("max_output_tokens", test.maxOutputTokens());
        switch (test.id()) {
            case "CHAT_A2" -> {
                request.put("system_instruction", "Role-play a restaurant waiter for an A2 English learner. Stay in role. Use short simple English. Respond in at most 40 words and ask one relevant question. Do not interrupt to correct grammar.");
                request.put("input", "Learner: I want order a chicken soup.");
            }
            case "CHAT_B1" -> {
                request.put("system_instruction", "Role-play a hotel receptionist for a B1 English learner. Respond in at most 70 English words. Stay in role, offer a practical option and ask one follow-up question. No CEFR scoring.");
                request.put("input", "Learner: My train was delayed and I arrived a day late. Can I change my booking without paying for the night I missed?");
            }
            case "FEEDBACK" -> {
                request.put("system_instruction", "Analyze only the supplied synthetic learner messages. Return Vietnamese explanations. Distinguish actual ERROR from IMPROVEMENT. Quote exact source text and source_message_id. Do not invent errors in correct sentences. No pronunciation or CEFR scores.");
                request.put("input", JSON.writeValueAsString(SOURCE_MESSAGES));
                ObjectNode format = request.putObject("response_format");
                format.put("type", "text").put("mime_type", "application/json");
                format.set("schema", feedbackSchema());
            }
            case "TRANSCRIPTION" -> {
                if (syntheticWav.length == 0 || syntheticWav.length > 2_000_000) {
                    throw new IllegalArgumentException("Synthetic WAV fixture missing or too large.");
                }
                request.put("system_instruction", "Transcribe the English speech exactly. Return only the transcript. Do not add, translate, score pronunciation or infer speaker identity.");
                var input = request.putArray("input");
                input.addObject().put("type", "text").put("text", "Transcribe this synthetic English utterance.");
                input.addObject().put("type", "audio").put("mime_type", "audio/wav")
                        .put("data", Base64.getEncoder().encodeToString(syntheticWav));
            }
            default -> throw new IllegalArgumentException("Unsupported probe case.");
        }
        return request;
    }

    static JsonNode feedbackSchema() throws Exception {
        return JSON.readTree("""
            {"type":"object","properties":{
              "summary":{"type":"string"},"strengths":{"type":"array","items":{"type":"string"}},
              "corrections":{"type":"array","items":{"type":"object","properties":{
                "source_message_id":{"type":"string","enum":["u1","u2","u3"]},
                "original_text":{"type":"string"},"suggested_text":{"type":"string"},
                "item_type":{"type":"string","enum":["ERROR","IMPROVEMENT"]},
                "category":{"type":"string","enum":["GRAMMAR","VOCABULARY","EXPRESSION"]},
                "explanation_vi":{"type":"string"}},
                "required":["source_message_id","original_text","suggested_text","item_type","category","explanation_vi"]}},
              "vocabulary":{"type":"array","items":{"type":"string"}},"next_practice":{"type":"string"}},
              "required":["summary","strengths","corrections","vocabulary","next_practice"]}
            """);
    }

    static boolean validFeedback(String text) {
        try {
            JsonNode result = JSON.readTree(text);
            if (result == null || !result.isObject() || !result.path("summary").isTextual()
                    || !result.path("next_practice").isTextual() || !stringArray(result.path("strengths"))
                    || !stringArray(result.path("vocabulary")) || !result.path("corrections").isArray()) return false;
            for (JsonNode correction : result.path("corrections")) {
                String id = correction.path("source_message_id").asText("");
                String original = correction.path("original_text").asText("");
                if (!SOURCE_MESSAGES.containsKey(id) || original.isBlank() || !SOURCE_MESSAGES.get(id).contains(original)
                        || !correction.path("suggested_text").isTextual() || !correction.path("explanation_vi").isTextual()
                        || !List.of("ERROR", "IMPROVEMENT").contains(correction.path("item_type").asText())
                        || !List.of("GRAMMAR", "VOCABULARY", "EXPRESSION").contains(correction.path("category").asText())) return false;
            }
            return true;
        } catch (Exception ignored) { return false; }
    }

    static boolean stringArray(JsonNode node) {
        if (!node.isArray()) return false;
        for (JsonNode item : node) if (!item.isTextual()) return false;
        return true;
    }

    static String outputText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        for (JsonNode step : response.path("steps")) {
            if (!"model_output".equals(step.path("type").asText())) continue;
            for (JsonNode part : step.path("content")) {
                if ("text".equals(part.path("type").asText()) && part.path("text").isTextual()) {
                    text.append(part.path("text").asText());
                }
            }
        }
        return text.toString();
    }

    public static void main(String[] args) throws Exception {
        boolean live = List.of(args).contains("--live");
        Map<String, String> env = System.getenv();
        Path workspace = Path.of(System.getProperty("phase0.workspace", ".")).toAbsolutePath().normalize();
        Path runtime = workspace.resolve(".runtime/phase0");
        Files.createDirectories(runtime);
        ObjectNode report = JSON.createObjectNode();
        report.put("recorded_at", Instant.now().toString()).put("mode", live ? "LIVE" : "DRY_RUN");
        report.put("api_revision", "2026-05-20").put("max_calls_per_run", 4);
        report.put("billing_hard_cap_enforced", false);
        report.put("note", "Request/output limits are not a provider billing hard cap. No automatic retries. No raw prompt, response, key or audio in reports.");
        var results = report.putArray("results");
        byte[] audio = new byte[0];
        HttpClient http = null;
        if (live) {
            checkLivePermission(env);
            Path fixture = runtime.resolve("synthetic-utterance.wav");
            Path manifest = runtime.resolve("synthetic-audio.json");
            audio = Files.readAllBytes(fixture);
            JsonNode provenance = JSON.readTree(Files.readString(manifest));
            String sha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(audio));
            if (!"WINDOWS_SAPI_SYNTHETIC".equals(provenance.path("source").asText())
                    || !sha.equals(provenance.path("sha256").asText())) {
                throw new IllegalStateException("Only the generated synthetic fixture with a matching manifest is allowed.");
            }
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
        }
        for (Case test : CASES) {
            ObjectNode result = results.addObject();
            String model = env.getOrDefault(test.modelVariable(), "");
            result.put("case_id", test.id()).put("task", test.task())
                    .put("model", model.isBlank() ? "NOT_SELECTED" : model.matches("[a-z0-9][a-z0-9._-]{1,100}") ? model : "INVALID_CONFIG")
                    .put("max_output_tokens", test.maxOutputTokens());
            if (!live) {
                buildRequest(test, "offline-not-a-model", test.id().equals("TRANSCRIPTION") ? new byte[]{1} : audio);
                result.put("status", "PREPARED_NOT_EXECUTED");
                continue;
            }
            ObjectNode requestBody = buildRequest(test, model, audio);
            HttpRequest request = HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(60))
                    .header("x-goog-api-key", env.get("GEMINI_API_KEY"))
                    .header("Content-Type", "application/json").header("Api-Revision", "2026-05-20")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(requestBody))).build();
            long start = System.nanoTime();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                result.put("latency_ms", (System.nanoTime() - start) / 1_000_000);
                result.put("http_status", response.statusCode());
                if (response.statusCode() != 200) {
                    result.put("status", "HTTP_ERROR_NO_RETRY");
                    break;
                }
                JsonNode body = JSON.readTree(response.body());
                String text = outputText(body);
                boolean complete = "completed".equals(body.path("status").asText());
                boolean valid = complete && !text.isBlank() && (!test.id().equals("FEEDBACK") || validFeedback(text));
                result.put("status", valid ? "VALIDATED_BASIC" : "INVALID_OUTPUT");
                result.put("output_characters", text.length()).put("human_quality_review", "NOT_PERFORMED");
                ObjectNode usage = result.putObject("usage");
                for (String field : List.of("total_input_tokens", "total_output_tokens", "total_tokens")) {
                    JsonNode value = body.path("usage").path(field);
                    if (value.isIntegralNumber() && value.asLong() >= 0) usage.set(field, value);
                }
                result.putNull("estimated_cost_usd");
                if (!valid) break;
            } catch (HttpTimeoutException ex) {
                result.put("status", "UNKNOWN_TIMEOUT_NO_RETRY");
                break;
            } catch (Exception ex) {
                result.put("status", "ERROR_NO_RETRY").put("error_type", ex.getClass().getSimpleName());
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                break;
            }
        }
        Path destination = runtime.resolve(live ? "gemini-live-report.json" : "gemini-dry-run.json");
        Files.writeString(destination, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.println("Probe report: " + destination + "; mode=" + (live ? "LIVE" : "DRY_RUN") + "; raw content not logged.");
    }
}
