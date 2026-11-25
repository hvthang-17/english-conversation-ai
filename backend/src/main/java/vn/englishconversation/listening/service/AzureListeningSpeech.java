package vn.englishconversation.listening.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.englishconversation.common.exception.ApiException;

@Component
@Profile({"g4", "g5", "g6"})
public class AzureListeningSpeech {
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
      .followRedirects(HttpClient.Redirect.NEVER).build();
  private final boolean enabled;
  private final String key;
  private final String region;

  public AzureListeningSpeech(@Value("${app.listening.azure.enabled:false}") boolean enabled,
      @Value("${app.listening.azure.key:}") String key, @Value("${app.listening.azure.region:}") String region) {
    this.enabled = enabled;
    this.key = key;
    this.region = region;
  }

  public byte[] synthesize(String ssml) {
    if (!enabled || key.isBlank() || !region.matches("[a-z][a-z0-9]{1,30}"))
      throw new ApiException(503, "LISTENING_TTS_DISABLED", "Chưa bật Azure Speech cho công khai đề.");
    var request = HttpRequest.newBuilder(URI.create("https://" + region + ".tts.speech.microsoft.com/cognitiveservices/v1"))
        .timeout(Duration.ofSeconds(20))
        .header("Ocp-Apim-Subscription-Key", key)
        .header("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
        .header("User-Agent", "EnglishConversationG4")
        .header("Content-Type", "application/ssml+xml")
        .POST(HttpRequest.BodyPublishers.ofString(ssml)).build();
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() == 429)
        throw new ApiException(503, "LISTENING_TTS_QUOTA", "Azure đang giới hạn lượt tạo audio. Hãy thử lại sau.");
      if (response.statusCode() != 200 || !response.headers().firstValue("Content-Type").orElse("")
          .matches("(?i)audio/mpeg(?:\\s*;.*)?"))
        throw new ApiException(502, "LISTENING_TTS_FAILED", "Azure không trả được audio hợp lệ.");
      return response.body();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw unavailable();
    } catch (IOException error) {
      throw unavailable();
    }
  }

  private static ApiException unavailable() {
    return new ApiException(503, "LISTENING_TTS_UNAVAILABLE", "Không nhận được audio từ Azure. Hãy thử lại.");
  }
}
