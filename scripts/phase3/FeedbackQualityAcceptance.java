import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.ai.config.ChatSettings;
import vn.englishconversation.ai.provider.GeminiTextProvider;
import vn.englishconversation.ai.provider.TextProvider.Outcome;
import vn.englishconversation.ai.provider.TextProvider.Prompt;
import vn.englishconversation.conversation.dto.MessageResponse;
import vn.englishconversation.feedback.dto.FeedbackResult;
import vn.englishconversation.feedback.service.FeedbackAnalysis;
import vn.englishconversation.feedback.service.FeedbackVocabulary;
import vn.englishconversation.feedback.service.FeedbackVocabulary.Reference;

/** Runs FB-05 against the fictional rubric, without starting Spring Boot or MySQL. */
public final class FeedbackQualityAcceptance {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String BASELINE = "practice-feedback-v3.2";
  private static final String LEARNER_ID = "102";
  private static final String PARTNER_ID = "101";
  private static final int RUBRIC_SIZE = 32;
  private static final Set<String> STOP_ERRORS = Set.of(
      "AI_NOT_CONFIGURED", "PROVIDER_AUTH", "PROVIDER_INVALID_ARGUMENT", "PROVIDER_REQUEST_REJECTED",
      "PROVIDER_RATE_LIMIT", "PROVIDER_TIMEOUT", "PROVIDER_CONNECT_FAILED", "PROVIDER_UNCERTAIN",
      "PROVIDER_TRANSPORT_UNKNOWN", "PROVIDER_INTERRUPTED", "SENSITIVE_OUTPUT");
  private static final Map<String, Scenario> SCENARIOS = Map.of(
      "mua hàng", new Scenario("Customer", "Cashier", "Hoàn tất mua hàng và xác nhận biên lai.",
          "At a shop checkout, the customer and cashier discuss the receipt."),
      "đổi hàng", new Scenario("Customer", "Returns clerk", "Giải thích việc trả hoặc đổi hàng.",
          "The customer brings an item back to the returns counter."),
      "ăn uống", new Scenario("Diner", "Server", "Trả lời thông tin để gọi món hoặc đồ uống.",
          "The server helps the diner at a restaurant."),
      "đặt phòng", new Scenario("Guest", "Receptionist", "Trao đổi các điều kiện đặt phòng.",
          "The guest and receptionist discuss a hotel booking. Details are established in the dialogue."),
      "làm quen", new Scenario("New member", "Club member", "Làm quen và chia sẻ thông tin cá nhân hư cấu.",
          "Two club members talk before an activity."),
      "hỏi đường", new Scenario("Visitor", "Local guide", "Hỏi hoặc xác nhận chỉ dẫn.",
          "A visitor and a local guide discuss destinations and directions."),
      "phỏng vấn", new Scenario("Applicant", "Interviewer", "Trả lời câu hỏi phỏng vấn về động lực và kinh nghiệm.",
          "An applicant discusses a fictional position with an interviewer."),
      "học nhóm", new Scenario("Student", "Classmate", "Thống nhất thời gian học nhóm.",
          "Two classmates are arranging a study meeting."));

  public record Settings(boolean live, boolean compareV32, String model, List<String> caseIds,
      int maxRequests, int requestsPerMinute, int timeoutSeconds, int contextLimit) {}
  public record Scenario(String learnerRole, String aiRole, String goal, String context) {}
  public record RubricCase(String id, String level, String topic, String mode, Scenario scenario,
      String question, String answer, String expectation, List<String> expectedLabels,
      String expectedQuestionQuality, String expectedAdvice, String expectedErrorRule) {}
  private record Work(String version, RubricCase sample, List<MessageResponse> transcript, Prompt prompt) {}
  public record Observed(String label, boolean labelMatches, String questionQuality, boolean questionQualityMatches,
      String adviceKind, Boolean expectedAdviceFound, List<String> errorRules,
      Boolean expectedErrorFound, int unexpectedErrorItems) {}
  public record CaseResult(String promptVersion, RubricCase sample, String status, String errorCode,
      String providerModel, Long latencyMs, Long inputTokens, Long outputTokens, Integer retryAfterSeconds,
      Observed observed, FeedbackResult feedback) {}
  public record LevelSummary(int targets, int validated, int matchingLabels) {}
  public record TokenSummary(int requestsWithUsage, Long total) {}
  public record PartialAnswerSummary(int targets, int correctlyPartial, int markedComplete) {}
  public record PromptSummary(String promptVersion, int targets, int processed, int validated, int matchingLabels,
      Map<String, LevelSummary> byLevel, PartialAnswerSummary partialAnswers,
      int questionQualityMatches, int adviceTargets, int expectedAdviceFound,
      int errorTargets, int expectedErrorsFound, int unexpectedErrorItems, int latencySamples,
      Double medianLatencyMs, Long p95LatencyMs, TokenSummary inputTokens, TokenSummary outputTokens) {}
  public record RunReport(String mode, String state, String startedAtUtc, String model, String fixtureSha256,
      String referencesSha256, int targets, int requestsPlanned, int maxRequests, int requestsAttempted,
      int requestsPerMinute, int timeoutSeconds, int contextLimit, String stopReason,
      List<PromptSummary> summaries, List<CaseResult> results) {}

  public static void main(String[] args) {
    try {
      Path root = Path.of(args[0]);
      Path output = Path.of(args[1]);
      var settings = JSON.readValue(Files.readString(output.resolve("settings.json")), Settings.class);
      System.exit(run(root, output, settings));
    } catch (IllegalArgumentException fail) {
      System.err.println(fail.getMessage());
      System.exit(1);
    } catch (IOException | UncheckedIOException fail) {
      System.err.println("Cannot initialize the run or read/write feedback acceptance evidence.");
      System.exit(1);
    } catch (InterruptedException fail) {
      Thread.currentThread().interrupt();
      System.err.println("Feedback acceptance interrupted. See the partial local report.");
      System.exit(1);
    }
  }

  private static int run(Path root, Path output, Settings settings) throws IOException, InterruptedException {
    var all = readRubric(root);
    var selected = all.stream()
        .filter(sample -> settings.caseIds().isEmpty() || settings.caseIds().contains(sample.id())).toList();
    check(!selected.isEmpty() && (settings.caseIds().isEmpty() || selected.size() == settings.caseIds().size()),
        "Select distinct rubric case IDs from 01 to 32.");
    var versions = settings.compareV32() ? List.of(BASELINE, FeedbackAnalysis.VERSION) : List.of(FeedbackAnalysis.VERSION);
    int planned = settings.live() ? selected.size() * versions.size() : 0;
    if (settings.live()) {
      check(settings.maxRequests() >= planned && settings.maxRequests() <= 2 * RUBRIC_SIZE,
          "The planned Gemini requests exceed the explicit limit (maximum 64).");
      check(settings.model().matches("[a-zA-Z0-9._-]{1,120}"), "Configure GEMINI_CHAT_MODEL or -Model.");
      check(settings.requestsPerMinute() >= 1 && settings.requestsPerMinute() <= 60,
          "RequestsPerMinute must be between 1 and 60.");
    }
    String key = settings.live() ? System.getenv("FB_ACCEPTANCE_API_KEY") : null;
    check(!settings.live() || key != null && key.matches("[a-zA-Z0-9_-]{20,256}"), "Configure GEMINI_API_KEY.");
    check(!settings.live() || !settings.model().contains(key), "Provide a model identifier rather than a credential.");

    var environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("feedbackAcceptance", Map.of(
        "app.gemini.enabled", settings.live(),
        "GEMINI_API_KEY", key == null ? "" : key,
        "GEMINI_CHAT_MODEL", settings.live() ? settings.model() : "",
        "app.chat.timeout-seconds", settings.timeoutSeconds(),
        "app.chat.context-limit", settings.contextLimit())));
    var chatSettings = new ChatSettings(environment);
    // Offline needs only payload(), so it never initializes the HTTP client's socket selector.
    var provider = settings.live() ? new GeminiTextProvider(chatSettings)
        : new GeminiTextProvider(chatSettings, null, URI.create("http://127.0.0.1"));
    var analysis = new FeedbackAnalysis();
    var catalog = starterReferences(root);
    var work = prepare(analysis, selected, versions, root, settings.contextLimit());
    String started = Instant.now().toString();
    String fixtureHash = sha256(selected);
    String referenceHash = sha256(catalog);
    List<CaseResult> results = new ArrayList<>();
    int attempts = 0;
    String stop = null;
    long nextAllowed = System.nanoTime();
    writeReport(output, settings, selected, versions, started, fixtureHash, referenceHash, attempts, "RUNNING", null, results);
    for (var item : work) {
      Long latency = null;
      Outcome outcome;
      if (settings.live()) {
        long remaining = nextAllowed - System.nanoTime();
        if (remaining > 0) Thread.sleep(Duration.ofNanos(remaining));
        attempts++;
        writeReport(output, settings, selected, versions, started, fixtureHash, referenceHash,
            attempts, "RUNNING", null, results);
        long start = System.nanoTime();
        nextAllowed = start + (60_000_000_000L + settings.requestsPerMinute() - 1) / settings.requestsPerMinute();
        outcome = provider.generate(item.prompt());
        latency = Duration.ofNanos(System.nanoTime() - start).toMillis();
      } else {
        check(JSON.readTree(provider.payload(item.prompt())).path("generationConfig")
            .path("responseMimeType").asString().equals("application/json"), "Expected FEEDBACK JSON mode.");
        outcome = fixtureOutcome(item.sample());
      }
      var result = evaluate(item, outcome, analysis, catalog, latency, key);
      results.add(result);
      System.out.printf("%s %s %s%n", item.version(), item.sample().id(), result.status());
      if (settings.live() && result.errorCode() != null && STOP_ERRORS.contains(result.errorCode())) {
        stop = result.errorCode();
        break;
      }
      writeReport(output, settings, selected, versions, started, fixtureHash, referenceHash,
          attempts, "RUNNING", null, results);
    }
    writeReport(output, settings, selected, versions, started, fixtureHash, referenceHash,
        attempts, stop == null ? "COMPLETED" : "STOPPED", stop, results);
    return stop == null && results.stream().allMatch(row -> row.feedback() != null) ? 0 : 2;
  }

  private static List<RubricCase> readRubric(Path root) throws IOException {
    var lines = Files.readAllLines(root.resolve("docs/development/chat-acceptance/feedback-v3-rubric.md"));
    var result = new ArrayList<RubricCase>();
    for (String line : lines) {
      if (!line.matches("^\\|\\s*[0-9]{2}\\s*\\|.*")) continue;
      var cells = line.split("\\|", -1);
      check(cells.length == 7, "A rubric row must have five columns.");
      String id = cells[1].strip();
      check(id.equals("%02d".formatted(result.size() + 1)), "Rubric IDs must be ordered from 01 to 32.");
      var heading = cells[2].strip().split("\\s*·\\s*");
      check(heading.length == 2 && Set.of("A2", "B1").contains(heading[0]) && SCENARIOS.containsKey(heading[1]),
          "Invalid rubric level or scenario.");
      int number = Integer.parseInt(id);
      var labels = switch (number) {
        case 3, 6, 9, 17, 22 -> List.of("PARTIAL");
        case 11, 27 -> List.of("UNCERTAIN");
        case 13 -> List.of("OFF_TOPIC");
        case 19 -> List.of("PARTIAL", "OFF_TOPIC");
        default -> List.of("COMPLETE");
      };
      String quality = switch (number) {
        case 11 -> "AMBIGUOUS";
        case 27 -> "OFF_ROLE";
        default -> "CLEAR";
      };
      String advice = switch (number) {
        case 3, 13, 17 -> "FILL_IN";
        case 11 -> "CLARIFY";
        default -> null;
      };
      var mode = Set.of(9, 10, 16, 23, 24, 26, 28, 32).contains(number) ? "VOICE" : "TEXT";
      result.add(new RubricCase(id, heading[0], heading[1], mode, SCENARIOS.get(heading[1]),
          cells[3].strip(), cells[4].strip(), cells[5].strip(), labels, quality, advice,
          number == 15 || number == 21 ? "VERB_FORM" : null));
    }
    check(result.size() == RUBRIC_SIZE && result.stream().filter(sample -> sample.level().equals("A2")).count() == 16,
        "Expected 32 rubric rows, with 16 cases at each level.");
    return List.copyOf(result);
  }

  private static List<Work> prepare(FeedbackAnalysis analysis, List<RubricCase> cases, List<String> versions,
      Path root, int contextLimit) throws IOException {
    String baseline = null;
    if (versions.contains(BASELINE)) {
      check("practice-feedback-v3.4".equals(FeedbackAnalysis.VERSION),
          "Recheck baseline schema compatibility before comparing a newer FEEDBACK version.");
      baseline = Files.readString(root.resolve("scripts/phase3/feedback-v3.2-prompt.txt")).stripTrailing() + "\n";
    }
    var work = new ArrayList<Work>();
    for (var sample : cases) {
      var transcript = List.of(message(PARTNER_ID, "ASSISTANT", sample.question()),
          message(LEARNER_ID, "LEARNER", sample.answer()));
      var roles = sample.scenario();
      var prompt = analysis.prompt(sample.level(), sample.topic(), roles.goal(), roles.learnerRole(),
          roles.aiRole(), roles.context(), sample.mode(), transcript, contextLimit);
      for (var version : versions) {
        Prompt selected = prompt;
        if (BASELINE.equals(version)) {
          String instruction = baseline + "\nReturn only a JSON object matching this schema:\n"
              + JSON.writeValueAsString(prompt.schema());
          check(instruction.length() + prompt.history().getFirst().content().length() <= contextLimit,
              "Baseline prompt does not fit CHAT_CONTEXT_LIMIT.");
          selected = new Prompt(instruction, prompt.history(), prompt.maxOutputTokens(), prompt.schema(), null, true);
        }
        work.add(new Work(version, sample, transcript, selected));
      }
    }
    return work;
  }

  private static MessageResponse message(String id, String sender, String text) {
    return new MessageResponse(id, "1", "ASSISTANT".equals(sender) ? 0 : 1, sender, text, "", null);
  }

  private static List<Reference> starterReferences(Path root) throws IOException {
    // A stable seed snapshot rather than the live catalog or learner database.
    var seed = JSON.readTree(Files.readString(root.resolve("backend/src/main/resources/content/starter-language-materials.json")));
    var references = new ArrayList<Reference>();
    for (var lesson : seed) {
      for (var item : lesson.path("items")) {
        String expression = item.path("expression").asString();
        String partOfSpeech = switch (expression) {
          case "usually", "straight" -> "ADVERB";
          case "practice", "exchange" -> "VERB";
          case "Nice to meet you." -> "EXPRESSION";
          default -> "WORD".equals(item.path("kind").asString()) ? "NOUN" : "SENTENCE";
        };
        references.add(new Reference(expression, partOfSpeech, item.path("meaningVi").asString(),
            item.path("exampleEn").asString()));
      }
    }
    check(!references.isEmpty(), "Starter vocabulary references are missing.");
    return List.copyOf(references);
  }

  private static CaseResult evaluate(Work item, Outcome outcome, FeedbackAnalysis analysis, List<Reference> catalog,
      Long latency, String key) {
    String failure = outcome.errorCode();
    String providerModel = outcome.model();
    FeedbackResult feedback = null;
    Observed observed = null;
    if (outcome.success()) {
      if (key != null && (outcome.text().contains(key) || providerModel != null && providerModel.contains(key))) {
        failure = "SENSITIVE_OUTPUT";
        providerModel = null;
      } else {
        try {
          feedback = FeedbackVocabulary.verify(analysis.validate(outcome.text(), item.transcript(), item.prompt()), catalog);
        } catch (IllegalArgumentException invalid) {
          failure = "FEEDBACK_INVALID";
        }
      }
    }
    if (feedback != null) {
      var sample = item.sample();
      var turn = feedback.coaching().turns().getFirst();
      var errors = feedback.corrections().stream().filter(correction -> "ERROR".equals(correction.itemType()))
          .map(FeedbackResult.Correction::ruleCode).toList();
      observed = new Observed(turn.status(), sample.expectedLabels().contains(turn.status()), turn.questionQuality(),
          sample.expectedQuestionQuality().equals(turn.questionQuality()), turn.adviceKind(),
          sample.expectedAdvice() == null ? null : sample.expectedAdvice().equals(turn.adviceKind()),
          errors, sample.expectedErrorRule() == null ? null : errors.contains(sample.expectedErrorRule()),
          (int) errors.stream().filter(rule -> !rule.equals(sample.expectedErrorRule())).count());
    }
    return new CaseResult(item.version(), item.sample(), feedback == null ? "FAILED" : "VALIDATED", failure,
        providerModel, latency, outcome.inputTokens(), outcome.outputTokens(),
        outcome.retryAfterSeconds() > 0 ? outcome.retryAfterSeconds() : null, observed, feedback);
  }

  private static Outcome fixtureOutcome(RubricCase sample) {
    var root = JSON.createObjectNode();
    root.put("summary", "Phản hồi giả lập.").put("goalAssessment", "Mẫu kiểm tra cấu trúc.")
        .put("nextPractice", "Luyện trả lời theo câu hỏi.");
    root.putArray("strengths");
    var corrections = root.putArray("corrections");
    if (sample.expectedErrorRule() != null) {
      String original = sample.id().equals("15") ? "like play" : "learned communicate";
      String improved = sample.id().equals("15") ? "like playing" : "learned to communicate";
      corrections.addObject().put("sourceMessageId", LEARNER_ID).put("itemType", "ERROR").put("category", "GRAMMAR")
          .put("ruleCode", sample.expectedErrorRule()).put("originalText", original).put("suggestedText", improved)
          .put("explanationVi", "Điều chỉnh dạng động từ.");
    }
    var suggestions = root.putArray("suggestions");
    if (sample.id().equals("01")) {
      suggestions.addObject().put("itemType", "VOCABULARY").put("expression", "receipt").put("meaningVi", "Biên lai")
          .put("exampleEn", "Could I have a receipt?").put("partOfSpeech", "NOUN").put("sourceKind", "IN_TRANSCRIPT")
          .put("sourceMessageId", PARTNER_ID).put("sourceExcerpt", sample.question()).put("observedForm", "receipt")
          .put("useVi", "Dùng khi thanh toán.");
    }
    String advice = sample.expectedAdvice() == null ? "NONE" : sample.expectedAdvice();
    var turn = root.putArray("turns").addObject().put("learnerMessageId", LEARNER_ID).put("assistantMessageId", PARTNER_ID)
        .put("status", sample.expectedLabels().getFirst()).put("questionQuality", sample.expectedQuestionQuality())
        .put("explanationVi", sample.expectation()).put("adviceKind", advice);
    switch (advice) {
      case "FILL_IN" -> turn.put("suggestionEn", switch (sample.id()) {
        case "03" -> "I want to return it because [reason].";
        case "17" -> "Yes. For example, I [describe an example of teamwork].";
        default -> "I'd like to join because [reason].";
      });
      case "CLARIFY" -> turn.put("suggestionEn", "What does seven refer to?");
      default -> turn.putNull("suggestionEn");
    }
    root.putArray("goals").addObject().put("labelVi", "Trả lời câu hỏi").put("status", "UNCERTAIN").putArray("learnerMessageIds");
    root.putNull("replay");
    return new Outcome(root.toString(), null, null, null, null, false, null, false, false, 0);
  }

  private static PromptSummary summarize(String version, List<RubricCase> cases, List<CaseResult> results) {
    var rows = results.stream().filter(result -> version.equals(result.promptVersion())).toList();
    var observed = rows.stream().map(CaseResult::observed).filter(java.util.Objects::nonNull).toList();
    Map<String, LevelSummary> levels = new LinkedHashMap<>();
    for (String level : List.of("A2", "B1")) {
      var validAtLevel = rows.stream().filter(row -> level.equals(row.sample().level()) && row.observed() != null).toList();
      levels.put(level, new LevelSummary((int) cases.stream().filter(sample -> level.equals(sample.level())).count(),
          validAtLevel.size(), (int) validAtLevel.stream().filter(row -> row.observed().labelMatches()).count()));
    }
    var latency = rows.stream().map(CaseResult::latencyMs).filter(java.util.Objects::nonNull).sorted().toList();
    int size = latency.size();
    Double median = size == 0 ? null : (latency.get((size - 1) / 2) + latency.get(size / 2)) / 2.0;
    Long p95 = size == 0 ? null : latency.get((int) Math.ceil(size * .95) - 1);
    var partial = rows.stream().filter(row -> row.sample().expectedLabels().equals(List.of("PARTIAL")))
        .map(CaseResult::observed).filter(java.util.Objects::nonNull).toList();
    var partialAnswers = new PartialAnswerSummary(
        (int) cases.stream().filter(sample -> sample.expectedLabels().equals(List.of("PARTIAL"))).count(),
        (int) partial.stream().filter(row -> "PARTIAL".equals(row.label())).count(),
        (int) partial.stream().filter(row -> "COMPLETE".equals(row.label())).count());
    return new PromptSummary(version, cases.size(), rows.size(), observed.size(),
        (int) observed.stream().filter(Observed::labelMatches).count(), levels, partialAnswers,
        (int) observed.stream().filter(Observed::questionQualityMatches).count(),
        (int) cases.stream().filter(sample -> sample.expectedAdvice() != null).count(),
        (int) observed.stream().filter(row -> Boolean.TRUE.equals(row.expectedAdviceFound())).count(),
        (int) cases.stream().filter(sample -> sample.expectedErrorRule() != null).count(),
        (int) observed.stream().filter(row -> Boolean.TRUE.equals(row.expectedErrorFound())).count(),
        observed.stream().mapToInt(Observed::unexpectedErrorItems).sum(), size, median, p95,
        tokens(rows.stream().map(CaseResult::inputTokens).toList()),
        tokens(rows.stream().map(CaseResult::outputTokens).toList()));
  }

  private static TokenSummary tokens(List<Long> values) {
    var observed = values.stream().filter(java.util.Objects::nonNull).toList();
    return new TokenSummary(observed.size(), observed.isEmpty() ? null : observed.stream().mapToLong(Long::longValue).sum());
  }

  private static void writeReport(Path output, Settings settings, List<RubricCase> cases, List<String> versions,
      String started, String fixtureHash, String referenceHash, int attempts, String state, String stop,
      List<CaseResult> results) throws IOException {
    var summaries = versions.stream().map(version -> summarize(version, cases, results)).toList();
    var report = new RunReport(settings.live() ? "LIVE" : "OFFLINE_FIXTURE", state, started,
        settings.live() ? settings.model() : null, fixtureHash, referenceHash, cases.size(),
        settings.live() ? cases.size() * versions.size() : 0, settings.maxRequests(), attempts,
        settings.requestsPerMinute(), settings.timeoutSeconds(), settings.contextLimit(), stop, summaries, results);
    Path temporary = output.resolve("report.tmp");
    Files.writeString(temporary, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    Files.move(temporary, output.resolve("report.json"), StandardCopyOption.REPLACE_EXISTING);
  }

  private static String sha256(Object value) {
    try {
      byte[] bytes = JSON.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException("SHA-256 is unavailable.", missing);
    }
  }

  private static void check(boolean passed, String message) {
    if (!passed) throw new IllegalArgumentException(message);
  }
}
