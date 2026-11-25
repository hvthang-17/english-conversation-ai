package vn.englishconversation.pronunciation.dto;

import java.math.BigDecimal;
import java.util.List;

public final class PronunciationData {
  private PronunciationData() {}

  public record Drill(long id, long lessonId, String lessonTitle, String level,
                      String referenceText, String locale, String mode) {}

  public record Attempt(long id, long drillId, String drillMode, String referenceText, String locale,
                        String assessmentVersion, String status, String errorCode, String recognizedText,
                        BigDecimal overall, BigDecimal accuracy, BigDecimal fluency, BigDecimal completeness,
                        List<Word> words, String createdAt) {}

  public record History(List<Attempt> attempts, int page, boolean hasNext) {}

  public record Word(String word, BigDecimal accuracy, String errorCode, Long startMs, Long endMs,
                     List<Phoneme> phonemes) {}

  public record Phoneme(String expectedIpa, BigDecimal accuracy, Long startMs, Long endMs) {}

  public record Assessment(String recognizedText, BigDecimal overall, BigDecimal accuracy,
                           BigDecimal fluency, BigDecimal completeness, List<Word> words) {}
}
