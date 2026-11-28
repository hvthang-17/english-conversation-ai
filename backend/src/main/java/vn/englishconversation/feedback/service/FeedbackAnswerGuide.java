package vn.englishconversation.feedback.service;

import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import vn.englishconversation.conversation.dto.MessageResponse;
import vn.englishconversation.feedback.dto.FeedbackResult;
import vn.englishconversation.feedback.dto.FeedbackResult.Coaching;
import vn.englishconversation.feedback.dto.FeedbackResult.TurnReview;

/** Supplies reusable answer frames for clear questions when the model omitted a usable one. */
public final class FeedbackAnswerGuide {
  private static final Pattern SCHOOL_END = question(
      "what time do you (?:usually )?finish school|when (?:does your school end|do your classes end)");
  private static final Pattern SCHOOL_ARRIVAL = Pattern.compile(
      "(?i)^\\s*i (?:usually )?(?:go to|get to|arrive at|start) school at "
          + "(?:[0-9]{1,2}(?::[0-9]{2})?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"
          + "(?:\\s*(?:a\\.?m\\.?|p\\.?m\\.?))?[.!]?\\s*$");
  private static final String SCHOOL_END_FRAME = "I finish school at [time].";
  private static final List<Frame> FRAMES = List.of(
      new Frame(SCHOOL_END, SCHOOL_END_FRAME),
      new Frame("why (?:would you like to|do you want to|are you going to) return (?:it|this|the item)",
          "I’d like to return it because [reason]."),
      new Frame("why (?:do you want to|would you like to) join(?: (?:us|the club))?",
          "I’d like to join because [reason]."),
      new Frame("can you give (?:me )?an example of teamwork",
          "One example is when I [describe what you did with your team]."));
  private static final Pattern DAY_AND_TIME = question("which day and time work for you");
  private static final Pattern DAY = Pattern.compile(
      "(?i)^\\s*(?:on )?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)(?:,? please)?[.!]?\\s*$");

  private FeedbackAnswerGuide() {}

  public static boolean needsFrame(FeedbackResult result) {
    return result != null && result.coaching() != null && result.coaching().turns().stream()
        .anyMatch(FeedbackAnswerGuide::missingFrame);
  }

  public static FeedbackResult withFrames(FeedbackResult result, List<MessageResponse> transcript) {
    if (result == null || result.coaching() == null) return result;
    var messages = new HashMap<String, String>();
    for (var message : transcript) messages.put(message.id(), message.content());
    var coaching = result.coaching();
    var turns = coaching.turns().stream().map(turn -> withFrame(turn, messages.get(turn.assistantMessageId()),
        messages.get(turn.learnerMessageId()))).toList();
    if (turns.equals(coaching.turns())) return result;
    return new FeedbackResult(result.summary(), result.strengths(), result.goalAssessment(), result.corrections(),
        result.suggestions(), result.nextPractice(),
        new Coaching(coaching.analyzedTurns(), coaching.totalTurns(), turns, coaching.goals(), coaching.replay()));
  }

  private static boolean missingFrame(TurnReview turn) {
    return "CLEAR".equals(turn.questionQuality())
        && ("OFF_TOPIC".equals(turn.status()) || "PARTIAL".equals(turn.status()))
        && turn.suggestionEn() == null;
  }

  private static TurnReview withFrame(TurnReview turn, String partner, String learner) {
    if (partner == null || learner == null || !"CLEAR".equals(turn.questionQuality())) return turn;
    if (SCHOOL_END.matcher(partner).find() && SCHOOL_ARRIVAL.matcher(learner).matches()) {
      return new TurnReview(turn.learnerMessageId(), turn.assistantMessageId(), "OFF_TOPIC", turn.questionQuality(),
          "Bạn đã nói giờ đến trường. Câu hỏi cần biết giờ bạn học xong.", "FILL_IN", SCHOOL_END_FRAME);
    }
    if (!missingFrame(turn)) return turn;
    for (var frame : FRAMES) {
      if (frame.question().matcher(partner).find()) return suggested(turn, frame.answer());
    }
    var day = DAY.matcher(learner);
    if (DAY_AND_TIME.matcher(partner).find() && day.matches()) {
      String name = day.group(1);
      return suggested(turn, Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase(java.util.Locale.ROOT)
          + " at [time] works for me.");
    }
    return turn;
  }

  private static TurnReview suggested(TurnReview turn, String frame) {
    return new TurnReview(turn.learnerMessageId(), turn.assistantMessageId(), turn.status(), turn.questionQuality(),
        turn.explanationVi(), "FILL_IN", frame);
  }

  private static Pattern question(String pattern) {
    return Pattern.compile("(?i)(?:^|[.!?]\\s+)\\s*(?:" + pattern + ")\\s*\\?\\s*$");
  }

  private record Frame(Pattern question, String answer) {
    private Frame(String pattern, String answer) {
      this(FeedbackAnswerGuide.question(pattern), answer);
    }
  }
}
