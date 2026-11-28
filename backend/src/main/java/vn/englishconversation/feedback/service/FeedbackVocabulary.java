package vn.englishconversation.feedback.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import vn.englishconversation.feedback.dto.FeedbackResult;
import vn.englishconversation.feedback.dto.FeedbackResult.Suggestion;
import vn.englishconversation.feedback.dto.FeedbackResult.SuggestionSource;

/** Uses reviewed references to decide which AI suggestions can become notebook cards. */
public final class FeedbackVocabulary {
  private static final Reference BOOK_A_ROOM =
      new Reference("book", "VERB", "đặt trước", "I'd like to book a room.");
  private static final Pattern BOOKING_CONTEXT =
      Pattern.compile("\\bbook(?:s|ed|ing)? (?:a |the |your |our )?(?:(?:quiet|single|double|hotel) )?rooms?\\b");

  private FeedbackVocabulary() {}

  public record Reference(String expression, String partOfSpeech, String meaningVi, String exampleEn) {}

  public static FeedbackResult verify(FeedbackResult result, List<Reference> catalog) {
    var verified = new ArrayList<Suggestion>();
    for (var suggestion : result.suggestions()) {
      var source = suggestion.source();
      if (source == null) {
        verified.add(suggestion);
        continue;
      }
      var matchingForm = new ArrayList<>(catalog.stream()
          .filter(ref -> normalize(ref.expression()).equals(normalize(suggestion.expression())))
          .filter(ref -> suggestion.partOfSpeech().name().equals(ref.partOfSpeech()))
          .toList());
      String context = "IN_TRANSCRIPT".equals(source.kind()) ? source.excerpt() : suggestion.exampleEn();
      if ("book".equals(normalize(suggestion.expression())) && "VERB".equals(suggestion.partOfSpeech().name())
          && BOOKING_CONTEXT.matcher(normalize(context)).find()) matchingForm.add(BOOK_A_ROOM);
      var reference = matchingForm.stream()
          .filter(ref -> sameMeaning(ref, suggestion.meaningVi()))
          .findFirst();
      if (reference.isEmpty() && !matchingForm.isEmpty()) continue;
      if (matchingForm.stream().map(ref -> normalize(ref.meaningVi())).distinct().count() > 1
          && "IN_TRANSCRIPT".equals(source.kind())) {
        reference = reference.filter(ref -> normalize(context).contains(normalize(ref.exampleEn())));
      }
      verified.add(new Suggestion(suggestion.itemType(), suggestion.expression(),
          reference.map(Reference::meaningVi).orElse(suggestion.meaningVi()),
          reference.map(Reference::exampleEn).orElse(suggestion.exampleEn()), suggestion.partOfSpeech(),
          new SuggestionSource(source.kind(), source.messageId(), source.excerpt(), source.observedForm(),
              source.useVi(), reference.isPresent())));
    }
    return new FeedbackResult(result.summary(), result.strengths(), result.goalAssessment(),
        result.corrections(), List.copyOf(verified), result.nextPractice(), result.coaching());
  }

  private static boolean sameMeaning(Reference reference, String proposed) {
    String meaning = normalize(proposed);
    if (normalize(reference.meaningVi()).equals(meaning)) return true;
    if ("receipt".equals(normalize(reference.expression()))
        && "hóa đơn mua hàng".equals(normalize(reference.meaningVi()))) {
      return Set.of("biên lai", "biên lai mua hàng", "hóa đơn").contains(meaning);
    }
    return BOOK_A_ROOM.equals(reference) && Set.of("đặt", "đặt phòng", "đặt chỗ").contains(meaning);
  }

  private static String normalize(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
  }
}
