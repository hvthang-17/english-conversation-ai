package vn.englishconversation.review.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.englishconversation.review.model.ReviewData.Rating.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class SpacedReviewScheduleTest {
  private final Instant now = Instant.parse("2026-11-27T02:00:00Z");

  @Test
  void handlesInitialIntervalsAndRelearning() {
    assertEquals(1, SpacedReviewSchedule.next(HARD, 0, now).days());
    assertEquals(3, SpacedReviewSchedule.next(GOOD, 0, now).days());
    assertEquals(7, SpacedReviewSchedule.next(EASY, 0, now).days());
    var forgotten = SpacedReviewSchedule.next(AGAIN, 30, now);
    assertEquals(0, forgotten.days());
    assertEquals(now.plus(10, ChronoUnit.MINUTES), forgotten.dueAt());
    assertEquals(SpacedReviewSchedule.next(AGAIN, 0, now), forgotten);
  }

  @Test
  void extendsFromTheActualRatingTimeWithAnUpperBound() {
    assertEquals(14, SpacedReviewSchedule.next(HARD, 11, now).days());
    assertEquals(22, SpacedReviewSchedule.next(GOOD, 11, now).days());
    assertEquals(33, SpacedReviewSchedule.next(EASY, 11, now).days());
    assertEquals(now.plus(180, ChronoUnit.DAYS), SpacedReviewSchedule.next(EASY, 179, now).dueAt());
  }
}
