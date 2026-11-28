package vn.englishconversation.testing;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.englishconversation.ai.provider.TextProvider;

/** Offline provider shared by integration tests; rejects calls inside transactions. */
public class FixtureProvider implements TextProvider {
    public void reset() {
      calls.set(0);
      timeout = false;
      transcriptionOutput = "A bowl of soup.";
      feedbackOutput = null;
      explanationOutput = VALID_EXPLANATION;
      lastPrompt = null;
      entered = null;
      release = null;
    }
    public final AtomicInteger calls = new AtomicInteger();
    public volatile boolean timeout;
    public volatile String transcriptionOutput = "A bowl of soup.";
    public volatile String feedbackOutput;
    public volatile String explanationOutput = VALID_EXPLANATION;
    public volatile Prompt lastPrompt;
    public volatile CountDownLatch entered;
    public volatile CountDownLatch release;

    public boolean ready() {
      return true;
    }

    public String model() {
      return "fixture-only";
    }

    public Outcome generate(Prompt prompt) {
      if (TransactionSynchronizationManager.isActualTransactionActive())
        throw new AssertionError("Provider called inside a transaction");
      calls.incrementAndGet();
      lastPrompt = prompt;
      if (entered != null) {
        entered.countDown();
        try {
          if (!release.await(8, TimeUnit.SECONDS))
            throw new AssertionError("Test did not release provider");
        } catch (InterruptedException failure) {
          throw new AssertionError(failure);
        }
      }
      if (timeout) {
        timeout = false;
        return Outcome.failure("PROVIDER_TIMEOUT", true, false, 0);
      }
      if (prompt.waveAudio() != null)
        return new Outcome(transcriptionOutput, "fixture-only", "wave-fixture",
            40L, 6L, false, null, false, false, 0);
      if (prompt.schema().toString().contains("simpleEnglish"))
        return new Outcome(explanationOutput, model(), "explanation-fixture", 21L, 8L,
            false, null, false, false, 0);
      if (!prompt.schema().isEmpty()) {
        assertTrue(prompt.instruction().contains("Never grade pronunciation"));
        return new Outcome(
            coachingFixture(feedbackOutput, prompt),
            "fixture-only",
            "feedback-fixture",
            31L,
            18L,
            false,
            null,
            false,
            false,
            0);
      }
      assertTrue(prompt.instruction().contains("A2") || prompt.instruction().contains("B1"));
      String reply = prompt.instruction().contains("Open naturally in the partner role")
          && prompt.instruction().contains("Play a customer")
          ? "Hi, I'd like to exchange this shirt." : "Hello! What would you like to order?";
      return new Outcome(
          reply,
          "fixture-only",
          "fixture-response",
          21L,
          8L,
          false,
          null,
          false,
          false,
          0);
    }

  public static final String VALID_EXPLANATION = """
      {"simpleEnglish":"What do you want to order?","meaningVi":"Bạn muốn gọi món gì?"}
      """;

  public static String coachingFixture(String output, TextProvider.Prompt prompt) {
    if (output == null) return null;
    try {
      var json = new tools.jackson.databind.ObjectMapper();
      var root = (tools.jackson.databind.node.ObjectNode) json.readTree(output);
      if (root.has("turns")) return output;
      var links = (tools.jackson.databind.node.ObjectNode) json.readTree(prompt.history().getFirst().content()).get("turnLinks");
      var turns = root.putArray("turns");
      var ids = root.putArray("goals").addObject().put("labelVi","Gọi món").put("status","PARTIAL")
          .putArray("learnerMessageIds");
      for (var entry : links.properties()) {
        ids.add(entry.getKey());
        turns.addObject().put("learnerMessageId",entry.getKey()).set("assistantMessageId",entry.getValue());
        var turn = (tools.jackson.databind.node.ObjectNode) turns.get(turns.size() - 1);
        turn.put("status","COMPLETE").put("questionQuality",entry.getValue().isNull() ? "NO_QUESTION" : "CLEAR")
            .put("explanationVi","Đã nêu ý chính.").put("adviceKind","NONE").putNull("suggestionEn");
      }
      root.putNull("replay");
      for (var item : root.path("suggestions")) {
        var suggestion = (tools.jackson.databind.node.ObjectNode) item;
        suggestion.put("sourceKind","TOPIC_EXTENSION").putNull("sourceMessageId")
            .putNull("sourceExcerpt").putNull("observedForm").put("useVi","Dùng để gọi thêm đồ uống.");
      }
      return json.writeValueAsString(root);
    } catch (RuntimeException malformed) {
      return output;
    }
  }

}
