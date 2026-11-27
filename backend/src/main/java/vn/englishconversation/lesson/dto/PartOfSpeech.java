package vn.englishconversation.lesson.dto;

import java.util.Arrays;

public enum PartOfSpeech {
  NOUN, VERB, ADJECTIVE, ADVERB, PRONOUN, DETERMINER, PREPOSITION, CONJUNCTION, INTERJECTION,
  NOUN_PHRASE, VERB_PHRASE, ADJECTIVE_PHRASE, ADVERB_PHRASE, PREPOSITIONAL_PHRASE, PHRASAL_VERB,
  SENTENCE, EXPRESSION, UNKNOWN;

  public static String[] codes() {
    return Arrays.stream(values()).map(Enum::name).toArray(String[]::new);
  }
}
