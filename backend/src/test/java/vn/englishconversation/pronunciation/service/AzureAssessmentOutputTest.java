package vn.englishconversation.pronunciation.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AzureAssessmentOutputTest {
  private final AzureAssessmentOutput output = new AzureAssessmentOutput(new ObjectMapper());
  private static final String RESULTS = """
      {"DisplayText":"How you there?","NBest":[{"PronunciationAssessment":{
        "PronScore":81.26,"AccuracyScore":84,"FluencyScore":95,"CompletenessScore":67,"ProsodyScore":90
      },"Words":[
        {"Word":"how","Offset":1500000,"Duration":2000000,
         "PronunciationAssessment":{"AccuracyScore":78,"ErrorType":"Mispronunciation"},
         "Phonemes":[{"Phoneme":"aʊ","Offset":1500000,"Duration":1000000,
           "PronunciationAssessment":{"AccuracyScore":59}}]},
        {"Word":"are","PronunciationAssessment":{"ErrorType":"Omission"}},
        {"Word":"you","PronunciationAssessment":{"AccuracyScore":95,"ErrorType":"None"}},
        {"Word":"there","PronunciationAssessment":{"AccuracyScore":0,"ErrorType":"Insertion"}}
      ]}]}
      """;

  @Test
  void returnsValidatedIpaTimingAndProviderMiscues() {
    var result = output.parse(RESULTS);
    assertEquals("How you there?", result.recognizedText());
    assertEquals(new BigDecimal("81.26"), result.overall());
    var how = result.words().getFirst();
    assertEquals("MISPRONUNCIATION", how.errorCode());
    assertEquals(150L, how.startMs());
    assertEquals(350L, how.endMs());
    assertEquals("aʊ", how.phonemes().getFirst().expectedIpa());
    assertEquals(250L, how.phonemes().getFirst().endMs());
    assertEquals("OMISSION", result.words().get(1).errorCode());
    assertNull(result.words().get(1).accuracy());
    assertEquals("INSERTION", result.words().getLast().errorCode());
  }

  @Test
  void leavesMissingScoresBlankAndRejectsInvalidScoresAndEvidence() {
    assertNull(output.parse(RESULTS.replace("\"FluencyScore\":95,", "")).fluency());
    assertNull(output.parse("{\"NBest\":[{\"Words\":[]}]}"));
    assertThrows(RuntimeException.class, () -> output.parse(RESULTS.replace("81.26", "100.1")));
    assertThrows(RuntimeException.class, () -> output.parse(RESULTS.replace("\"Omission\"", "\"unexpected\"")));
    assertThrows(RuntimeException.class, () -> output.parse("{\"NBest\":[{}]}"));
  }
}
