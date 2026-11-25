package vn.englishconversation.listening.service;

import static vn.englishconversation.listening.dto.ListeningData.*;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public final class ListeningRules {
  private static final Map<String, String> VOICES = Map.of(
      "en-us-voice-a-v1", "en-US-AriaNeural", "en-us-voice-b-v1", "en-US-GuyNeural");

  private ListeningRules() {}

  public static void validate(String title, String instructions, String voice, String transcript,
      List<Segment> segments, List<Question> questions) {
    require(text(title, 200) && text(instructions, 1000) && text(transcript, 4000) && voice != null
        && VOICES.containsKey(voice), "Invalid exercise text or voice");
    if (segments != null) {
      require(segments.size() >= 2 && segments.size() <= 16, "Invalid segment count");
      for (int i = 0; i < segments.size(); i++) {
        var segment = segments.get(i);
        require(segment != null && segment.lineNo() == i + 1 && text(segment.speakerLabel(), 80)
            && text(segment.textEn(), 600) && segment.voiceKey() != null && VOICES.containsKey(segment.voiceKey()),
            "Invalid segment, order or voice");
      }
      require(transcript.equals(String.join(" ", segments.stream().map(Segment::textEn).toList())),
          "Transcript must match the spoken segments");
    }
    require(questions != null && !questions.isEmpty() && questions.size() <= 12, "Invalid question count");
    var codes = new HashSet<String>();
    for (var question : questions) {
      require(question != null && code(question.code()) && codes.add(question.code()) && question.type() != null
          && text(question.prompt(), 500) && text(question.explanationVi(), 1000)
          && question.accepted() != null && !question.accepted().isEmpty(), "Invalid question or duplicate code");
      Set<String> options = new HashSet<>();
      for (var option : question.publicView().options()) {
        require(option != null && code(option.code()) && options.add(option.code()) && text(option.text(), 400),
            "Invalid option or duplicate code");
      }
      switch (question.type()) {
        case MULTIPLE_CHOICE -> require(options.size() >= 2 && options.size() <= 8
            && question.accepted().size() == 1 && options.contains(question.accepted().getFirst()), "Invalid choice key");
        case REORDER -> require(options.size() >= 2 && options.size() <= 8 && question.accepted().size() == options.size()
            && options.equals(new HashSet<>(question.accepted())), "Invalid reorder key");
        case FILL_BLANK, DICTATION -> require(options.isEmpty() && question.accepted().size() <= 12
            && question.accepted().stream().allMatch(answer -> text(answer, 400) && !normalize(answer).isEmpty()),
            "Invalid text answer key");
      }
    }
  }

  /** Returns an ordered map for scoring and exact replay comparison, omitting blank answers. */
  public static Map<String, JsonNode> answers(List<Question> questions, Map<String, JsonNode> supplied) {
    Set<String> codes = new HashSet<>(questions.stream().map(Question::code).toList());
    require(codes.containsAll(supplied.keySet()), "Unknown question");
    var result = new LinkedHashMap<String, JsonNode>();
    for (var question : questions) {
      JsonNode value = supplied.get(question.code());
      if (value == null || value.isNull() || (value.isString() && value.asString().isBlank())) continue;
      var options = question.publicView().options().stream().map(Option::code).toList();
      switch (question.type()) {
        case FILL_BLANK, DICTATION -> require(value.isString() && value.asString().length() <= 400, "Invalid text answer");
        case MULTIPLE_CHOICE -> require(value.isString() && options.contains(value.asString()), "Unknown choice");
        case REORDER -> {
          require(value.isArray() && value.size() <= options.size(), "Invalid ordering answer");
          if (value.isEmpty()) continue;
          var seen = new HashSet<String>();
          for (JsonNode entry : value) {
            require(entry.isString() && options.contains(entry.asString()) && seen.add(entry.asString()), "Invalid ordering item");
          }
        }
      }
      result.put(question.code(), value);
    }
    return result;
  }

  public static List<Graded> grade(List<Question> questions, Map<String, JsonNode> answers) {
    return questions.stream().map(question -> {
      var answer = answers.get(question.code());
      boolean correct = answer != null && switch (question.type()) {
        case MULTIPLE_CHOICE -> question.accepted().getFirst().equals(answer.asString());
        case REORDER -> question.accepted().equals(order(answer));
        case FILL_BLANK, DICTATION ->
            question.accepted().stream().map(ListeningRules::normalize).anyMatch(normalize(answer.asString())::equals);
      };
      return new Graded(question.code(), answer, correct ? 1 : 0, question.accepted(), question.explanationVi());
    }).toList();
  }

  public static String ssml(String voice, String transcript, List<Segment> segments) {
    var result = new StringBuilder("<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"en-US\">");
    if (segments == null) appendVoice(result, voice, transcript);
    else for (var line : segments) appendVoice(result, line.voiceKey(), line.textEn());
    return result.append("</speak>").toString();
  }

  private static void appendVoice(StringBuilder output, String voice, String text) {
    output.append("<voice name=\"").append(VOICES.get(voice)).append("\">")
        .append(text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")).append("</voice>");
  }

  private static List<String> order(JsonNode node) {
    var result = new java.util.ArrayList<String>();
    for (var entry : node) result.add(entry.asString());
    return result;
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
        .replaceAll("(?U)\\s+", " ").strip().replaceFirst("[.!?,;:]+$", "").strip();
  }

  private static boolean text(String value, int max) {
    return value != null && !value.isBlank() && value.length() <= max;
  }
  private static boolean code(String value) {
    return value != null && value.matches("[A-Za-z0-9_-]{1,32}");
  }
  private static void require(boolean valid, String message) {
    if (!valid) throw new IllegalArgumentException(message);
  }
}
