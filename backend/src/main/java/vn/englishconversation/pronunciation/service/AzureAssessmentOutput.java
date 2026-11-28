package vn.englishconversation.pronunciation.service;

import static vn.englishconversation.pronunciation.dto.PronunciationData.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@FeatureEnabled(FeatureEnabled.Feature.PRONUNCIATION)
public class AzureAssessmentOutput {
  private static final Set<String> ERROR_TYPES =
      Set.of("None", "Omission", "Insertion", "Mispronunciation");
  private final ObjectMapper json;

  public AzureAssessmentOutput(ObjectMapper json) {
    this.json = json;
  }

  Assessment parse(String response) {
    if (response == null || response.length() > 512_000) throw invalid();
    var root = json.readTree(response);
    var choice = root.path("NBest").path(0);
    var words = choice.path("Words");
    if (!words.isArray() || words.size() > 150 || !choice.isObject()) throw invalid();
    if (words.isEmpty()) return null;
    var text = label(root.path("DisplayText"), 2000);
    if (text == null) text = label(choice.path("Display"), 2000);
    if (text == null) return null;

    var scores = choice.path("PronunciationAssessment");
    var details = new ArrayList<Word>();
    boolean hasSpokenWord = false;
    for (var word : words) {
      var name = label(word.path("Word"), 100);
      var assessment = word.path("PronunciationAssessment");
      var type = assessment.path("ErrorType").asString("");
      if (name == null || !ERROR_TYPES.contains(type)) throw invalid();
      boolean omitted = type.equals("Omission");
      hasSpokenWord |= !omitted;
      var phonemes = new ArrayList<Phoneme>();
      var sounds = word.path("Phonemes");
      if (!sounds.isMissingNode() && (!sounds.isArray() || sounds.size() > 40)) throw invalid();
      if (!omitted) {
        for (var phoneme : sounds) {
          var ipa = label(phoneme.path("Phoneme"), 20);
          if (ipa == null) throw invalid();
          phonemes.add(new Phoneme(ipa, score(phoneme.path("PronunciationAssessment").path("AccuracyScore")),
              offset(phoneme), end(phoneme)));
        }
      }
      details.add(new Word(name, omitted ? null : score(assessment.path("AccuracyScore")),
          type.equals("None") ? null : type.toUpperCase(java.util.Locale.ROOT),
          omitted ? null : offset(word), omitted ? null : end(word), List.copyOf(phonemes)));
    }
    if (!hasSpokenWord) return null;
    return new Assessment(text, score(scores.path("PronScore")), score(scores.path("AccuracyScore")),
        score(scores.path("FluencyScore")), score(scores.path("CompletenessScore")), List.copyOf(details));
  }

  private static String label(JsonNode value, int limit) {
    if (value.isMissingNode() || value.isNull()) return null;
    if (!value.isString() || value.asString().length() > limit) throw invalid();
    return value.asString().isBlank() ? null : value.asString().strip();
  }

  private static BigDecimal score(JsonNode value) {
    if (value.isMissingNode() || value.isNull()) return null;
    if (!value.isNumber()) throw invalid();
    var score = value.decimalValue();
    if (score.signum() < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0) throw invalid();
    return score.setScale(2, RoundingMode.HALF_UP);
  }

  private static Long ticks(JsonNode value) {
    if (value.isMissingNode() || value.isNull()) return null;
    if (!value.isIntegralNumber() || !value.canConvertToLong()) throw invalid();
    long ticks = value.longValue();
    if (ticks < 0 || ticks > 600_000_000) throw invalid();
    return ticks;
  }

  private static Long offset(JsonNode node) {
    var value = ticks(node.path("Offset"));
    return value == null ? null : value / 10_000;
  }

  private static Long end(JsonNode node) {
    var start = ticks(node.path("Offset"));
    var duration = ticks(node.path("Duration"));
    if (start == null || duration == null) return null;
    if (start + duration > 600_000_000) throw invalid();
    return (start + duration) / 10_000;
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Invalid pronunciation response");
  }
}
