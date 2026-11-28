package vn.englishconversation.ai.provider;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import vn.englishconversation.ai.config.ChatSettings;

@Component
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class GeminiTextProvider implements TextProvider {
  private final ChatSettings settings;
  private final HttpClient client;
  private final URI endpoint;
  private final ObjectMapper json = new ObjectMapper();

  @org.springframework.beans.factory.annotation.Autowired
  public GeminiTextProvider(ChatSettings settings) {
    this(
        settings,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(),
        URI.create(
            "https://generativelanguage.googleapis.com/v1beta/models/"
                + (settings.model.isBlank() ? "not-configured" : settings.model)
                + ":generateContent"));
  }

  // Test constructor accepts a local server; runtime endpoint is fixed above, never supplied by
  // user input.
  public GeminiTextProvider(ChatSettings settings, HttpClient client, URI endpoint) {
    this.settings = settings;
    this.client = client;
    this.endpoint = endpoint;
  }

  @Override
  public boolean ready() {
    return settings.enabled && !settings.apiKey.isBlank() && !settings.model.isBlank();
  }

  @Override
  public String model() {
    return settings.model.isBlank() ? "NOT_CONFIGURED" : settings.model;
  }

  public String payload(Prompt prompt) {
    List<Map<String, Object>> contents = new ArrayList<>();
    for (var item : prompt.history()) {
      // Preserve all accepted text. Adjacent same-role messages are legal data, grouped for
      // transport.
      if (!contents.isEmpty() && contents.getLast().get("role").equals(item.role())) {
        @SuppressWarnings("unchecked")
        var parts = (List<Map<String, Object>>) contents.getLast().get("parts");
        parts.add(Map.of("text", item.content()));
      } else {
        var parts = new ArrayList<Map<String, Object>>();
        parts.add(Map.of("text", item.content()));
        contents.add(Map.of("role", item.role(), "parts", parts));
      }
    }
    if (prompt.waveAudio() != null) {
      contents.add(Map.of("role", "user", "parts", List.of(
          Map.of("text", "Transcribe this recording."),
          Map.of("inlineData", Map.of("mimeType", "audio/wav", "data",
              java.util.Base64.getEncoder().encodeToString(prompt.waveAudio()))))));
    }
    Map<String, Object> generation =
        new HashMap<>(
            Map.of(
                "candidateCount",
                1,
                "maxOutputTokens",
                prompt.maxOutputTokens(),
                "temperature",
                prompt.waveAudio() == null ? 0.6 : 0.0));
    if (!prompt.schema().isEmpty()) {
      generation.put("responseMimeType", "application/json");
      if (!prompt.schemaInInstruction()) generation.put("responseJsonSchema", prompt.schema());
    }
    return json.writeValueAsString(
        Map.of(
            "systemInstruction",
            Map.of("parts", List.of(Map.of("text", prompt.instruction()))),
            "contents",
            contents,
            "generationConfig",
            generation));
  }

  @Override
  public Outcome generate(Prompt prompt) {
    if (!ready()) return Outcome.failure("AI_NOT_CONFIGURED", false, false, 0);
    CompletableFuture<HttpResponse<byte[]>> future = null;
    try {
      var request =
          HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofSeconds(settings.timeoutSeconds))
              .header("Content-Type", "application/json")
              .header("x-goog-api-key", settings.apiKey)
              .POST(HttpRequest.BodyPublishers.ofString(payload(prompt)))
              .build();
      future = client.sendAsync(request, info -> new LimitedBody(65536));
      var response = future.get(settings.timeoutSeconds, TimeUnit.SECONDS);
      int status = response.statusCode();
      if (status == 429) {
        int retry = 5;
        try {
          retry =
              Math.max(
                  1,
                  Math.min(
                      60,
                      Integer.parseInt(response.headers().firstValue("retry-after").orElse("5"))));
        } catch (NumberFormatException ignored) {
        }
        return Outcome.failure("PROVIDER_RATE_LIMIT", false, true, retry);
      }
      if (status >= 500) return Outcome.failure("PROVIDER_UNCERTAIN", true, false, 0);
      if (status < 200 || status >= 300)
        return Outcome.failure(
            status == 401 || status == 403 ? "PROVIDER_AUTH" :
                status == 400 ? "PROVIDER_INVALID_ARGUMENT" : "PROVIDER_REQUEST_REJECTED",
            false,
            false,
            0);
      int limit = settings.outputLimit;
      if (!prompt.schema().isEmpty()) limit = 16000;
      if (prompt.waveAudio() != null) limit = settings.inputLimit;
      return parse(response.body(), limit);
    } catch (TimeoutException e) {
      if (future != null) future.cancel(true);
      return Outcome.failure("PROVIDER_TIMEOUT", true, false, 0);
    } catch (InterruptedException e) {
      if (future != null) future.cancel(true);
      Thread.currentThread().interrupt();
      return Outcome.failure("PROVIDER_INTERRUPTED", true, false, 0);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      boolean notSent =
          cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException;
      return Outcome.failure(
          notSent ? "PROVIDER_CONNECT_FAILED" : "PROVIDER_TRANSPORT_UNKNOWN", !notSent, notSent, 5);
    } catch (Exception e) {
      return Outcome.failure("PROVIDER_INVALID_RESPONSE", false, false, 0);
    }
  }

  public Outcome parse(byte[] bytes) {
    return parse(bytes, settings.outputLimit);
  }

  private Outcome parse(byte[] bytes, int outputLimit) {
    try {
      var root = json.readTree(bytes);
      var candidate = root.path("candidates").path(0);
      if (!"STOP".equals(candidate.path("finishReason").asString()))
        return Outcome.failure("PROVIDER_INCOMPLETE_OR_BLOCKED", false, false, 0);
      StringBuilder text = new StringBuilder();
      for (var part : candidate.path("content").path("parts")) {
        if (part.path("thought").asBoolean(false)) continue;
        if (part.has("functionCall") || !part.has("text") || !part.get("text").isString())
          return Outcome.failure("PROVIDER_NON_TEXT", false, false, 0);
        text.append(part.get("text").asString());
      }
      String value = text.toString().strip();
      if (value.isBlank()
          || value.length() > outputLimit
          || value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
        return Outcome.failure("PROVIDER_INVALID_TEXT", false, false, 0);
      var usage = root.path("usageMetadata");
      Long input = token(usage, "promptTokenCount"),
          output = token(usage, "candidatesTokenCount"),
          thought = token(usage, "thoughtsTokenCount");
      if (output != null && thought != null) output = Math.addExact(output, thought);
      String actual = safe(root.path("modelVersion").asString(""), 150);
      if (actual == null) actual = model();
      return new Outcome(
          value,
          actual,
          safe(root.path("responseId").asString(""), 255),
          input,
          output,
          usage.path("cachedContentTokenCount").asLong(0) > 0,
          null,
          false,
          false,
          0);
    } catch (Exception e) {
      return Outcome.failure("PROVIDER_INVALID_RESPONSE", false, false, 0);
    }
  }

  private Long token(JsonNode node, String name) {
    var v = node.get(name);
    return v != null && v.isIntegralNumber() && v.canConvertToLong() && v.asLong() >= 0
        ? v.asLong()
        : null;
  }

  private String safe(String value, int limit) {
    return value.length() > 0 && value.length() <= limit && value.matches("[a-zA-Z0-9._:/=-]+")
        ? value
        : null;
  }

  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private Flow.Subscription subscription;

    LimitedBody(int limit) {
      this.limit = limit;
    }

    public CompletionStage<byte[]> getBody() {
      return body;
    }

    public void onSubscribe(Flow.Subscription s) {
      subscription = s;
      s.request(1);
    }

    public void onNext(List<ByteBuffer> buffers) {
      for (var buffer : buffers) {
        if ((long) bytes.size() + buffer.remaining() > limit) {
          subscription.cancel();
          body.completeExceptionally(new IOException("RESPONSE_LIMIT"));
          return;
        }
        byte[] chunk = new byte[buffer.remaining()];
        buffer.get(chunk);
        bytes.writeBytes(chunk);
      }
      subscription.request(1);
    }

    public void onError(Throwable t) {
      body.completeExceptionally(t);
    }

    public void onComplete() {
      body.complete(bytes.toByteArray());
    }
  }
}
