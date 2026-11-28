package vn.englishconversation.pronunciation.service;

import com.microsoft.cognitiveservices.speech.CancellationDetails;
import com.microsoft.cognitiveservices.speech.CancellationErrorCode;
import com.microsoft.cognitiveservices.speech.OutputFormat;
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentConfig;
import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.pronunciation.dto.PronunciationData.Assessment;

@Component
@FeatureEnabled(FeatureEnabled.Feature.PRONUNCIATION)
public class AzurePronunciationProvider implements PronunciationProvider {
  private final boolean enabled;
  private final String key;
  private final String region;
  private final ObjectMapper json;
  private final AzureAssessmentOutput output;

  public AzurePronunciationProvider(@Value("${app.pronunciation.azure.enabled:false}") boolean enabled,
      @Value("${app.pronunciation.azure.key:}") String key,
      @Value("${app.pronunciation.azure.region:}") String region, ObjectMapper json,
      AzureAssessmentOutput output) {
    this.enabled = enabled;
    this.key = key;
    this.region = region;
    this.json = json;
    this.output = output;
  }

  @Override
  public boolean available() {
    return enabled && !key.isBlank() && region.matches("[a-z0-9]{2,40}");
  }

  @Override
  public Assessment assess(Path wav, String reference, String locale) {
    if (!available()) throw new Failure("AZURE_UNAVAILABLE", false, false);
    var settings = json.writeValueAsString(Map.of(
        "referenceText", reference == null ? "" : reference, "gradingSystem", "HundredMark", "granularity", "Phoneme",
        "phonemeAlphabet", "IPA", "enableMiscue", reference != null));
    try (var speech = SpeechConfig.fromSubscription(key, region);
         var audio = AudioConfig.fromWavFileInput(wav.toString());
         var config = PronunciationAssessmentConfig.fromJson(settings)) {
      speech.setSpeechRecognitionLanguage(locale);
      speech.setOutputFormat(OutputFormat.Detailed);
      try (var recognizer = new SpeechRecognizer(speech, audio)) {
        config.applyTo(recognizer);
        var pending = recognizer.recognizeOnceAsync();
        try (var result = pending.get(45, TimeUnit.SECONDS)) {
          if (result.getReason() == ResultReason.NoMatch) return null;
          if (result.getReason() == ResultReason.Canceled) {
            var details = CancellationDetails.fromResult(result);
            var code = details.getErrorCode();
            if (code == CancellationErrorCode.TooManyRequests)
              throw new Failure("AZURE_QUOTA", true, false);
            if (code == CancellationErrorCode.ServiceTimeout || code == CancellationErrorCode.ConnectionFailure)
              throw new Failure("AZURE_OUTCOME_UNKNOWN", false, true);
            throw new Failure("AZURE_REJECTED", false, false);
          }
          if (result.getReason() != ResultReason.RecognizedSpeech)
            throw new Failure("AZURE_INVALID_RESULT", false, false);
          try {
            return output.parse(result.getProperties().getProperty(PropertyId.SpeechServiceResponse_JsonResult));
          } catch (RuntimeException invalid) {
            throw new Failure("AZURE_INVALID_RESULT", false, false);
          }
        } catch (TimeoutException | ExecutionException incomplete) {
          pending.cancel(true);
          throw new Failure("AZURE_OUTCOME_UNKNOWN", false, true);
        } catch (InterruptedException interrupted) {
          pending.cancel(true);
          Thread.currentThread().interrupt();
          throw new Failure("AZURE_OUTCOME_UNKNOWN", false, true);
        }
      }
    } catch (LinkageError unavailable) {
      throw new Failure("AZURE_SDK_UNAVAILABLE", false, false);
    }
  }
}
