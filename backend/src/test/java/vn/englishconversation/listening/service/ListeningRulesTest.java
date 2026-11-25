package vn.englishconversation.listening.service;

import static org.junit.jupiter.api.Assertions.*;
import static vn.englishconversation.listening.dto.ListeningData.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ListeningRulesTest {
  private final ObjectMapper json = new ObjectMapper();
  private final List<Question> questions = List.of(
      new Question("choice", Kind.MULTIPLE_CHOICE, "Chọn", List.of(new Option("a","Tea"), new Option("b","Coffee")), List.of("a"), "Giải thích"),
      new Question("fill", Kind.FILL_BLANK, "Điền", null, List.of("two", "2"), "Giải thích"),
      new Question("sort", Kind.REORDER, "Xếp", List.of(new Option("b","Then"), new Option("a","First")), List.of("a","b"), "Giải thích"),
      new Question("write", Kind.DICTATION, "Chép", null, List.of("I’m ready", "I am ready"), "Giải thích"));

  @Test
  void scoresEachTypeAndOnlyExplicitTextVariants() {
    var correct = answer("""
        {"choice":"a","fill":"  TWO!  ","sort":["a","b"],"write":"I  AM READY."}
        """);
    var wrong = answer("""
        {"choice":"b","fill":"three","sort":["b","a"],"write":"I'm ready"}
        """);
    assertEquals(List.of(1, 1, 1, 1), score(correct));
    assertEquals(List.of(0, 0, 0, 0), score(wrong));
    assertEquals(List.of(0, 0, 0, 0), score(answer("{\"sort\":[],\"fill\":\"  \"}")));
    assertEquals(List.of(0, 0, 0, 0), score(answer("{\"sort\":[\"a\"]}")));
    assertThrows(IllegalArgumentException.class, () -> score(answer("{\"foreign\":\"a\"}")));
    assertThrows(IllegalArgumentException.class, () -> score(answer("{\"choice\":\"c\"}")));
    assertThrows(IllegalArgumentException.class, () -> score(answer("{\"sort\":[\"a\",\"a\"]}")));
  }

  @Test
  void validatesAuthoredContentAndEscapesSsml() {
    var voiceA = "en-us-voice-a-v1";
    var voiceB = "en-us-voice-b-v1";
    var first = new Segment(1, "A", "Tea & toast?", voiceA);
    var second = new Segment(2, "B", "Yes.", voiceB);
    String transcript = "Tea & toast? Yes.";
    ListeningRules.validate("Title", "Instruction", voiceA, transcript, List.of(first, second), questions);
    var ssml = ListeningRules.ssml(voiceA, transcript, List.of(first, second));
    assertTrue(ssml.contains("Tea &amp; toast?"));
    assertTrue(ssml.indexOf("AriaNeural") < ssml.indexOf("GuyNeural"));
    assertThrows(IllegalArgumentException.class, () ->
        ListeningRules.validate("Title", "Instruction", voiceA, transcript, List.of(second, first), questions));
    assertThrows(IllegalArgumentException.class, () ->
        ListeningRules.validate("Title", "Instruction", voiceA, transcript,
            List.of(first, new Segment(2, "B", "", voiceB)), questions));
    assertThrows(IllegalArgumentException.class, () ->
        ListeningRules.validate("Title", "Instruction", voiceA, transcript,
            List.of(first, new Segment(2, "B", "Yes.", "unknown")), questions));
    assertThrows(IllegalArgumentException.class, () ->
        ListeningRules.validate("Title", "Instruction", voiceA, transcript, null, List.of(questions.getFirst(), questions.getFirst())));
    assertThrows(IllegalArgumentException.class, () ->
        ListeningRules.validate("Title", "Instruction", voiceA, transcript, null,
            List.of(new Question("q", Kind.DICTATION, "Chép", null, List.of(), "Giải thích"))));
  }

  @Test
  void formatCheckRequiresConsecutiveMp3Headers() {
    var bytes = fixtureFrames();
    assertTrue(ListeningPublication.validMp3(bytes));
    bytes[144] = 0;
    assertFalse(ListeningPublication.validMp3(bytes));
    assertFalse(ListeningPublication.validMp3(new byte[0]));
  }

  public static byte[] fixtureFrames() {
    var bytes = new byte[288];
    for (int offset : new int[] {0, 144}) {
      bytes[offset] = (byte) 0xff;
      bytes[offset + 1] = (byte) 0xf3;
      bytes[offset + 2] = (byte) 0x64;
      bytes[offset + 3] = (byte) 0xc4;
    }
    return bytes;
  }

  private List<Integer> score(Map<String, JsonNode> answer) {
    return ListeningRules.grade(questions, ListeningRules.answers(questions, answer)).stream().map(Graded::pointsAwarded).toList();
  }

  private Map<String, JsonNode> answer(String source) {
    return json.readValue(source, new tools.jackson.core.type.TypeReference<>() {});
  }
}
