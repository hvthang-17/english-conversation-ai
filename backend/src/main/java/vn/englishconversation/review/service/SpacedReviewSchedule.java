package vn.englishconversation.review.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import vn.englishconversation.review.model.ReviewData.Rating;

final class SpacedReviewSchedule {
  static final String VERSION = "INTERVAL_V1";

  record Interval(int days, Instant dueAt) {}

  private SpacedReviewSchedule() {}

  static Interval next(Rating rating, int previousDays, Instant now) {
    if (rating == Rating.AGAIN) return new Interval(0, now.plus(10, ChronoUnit.MINUTES));
    int days = switch (rating) {
      case HARD -> previousDays == 0 ? 1 : Math.max(previousDays + 1, (previousDays * 6 + 4) / 5);
      case GOOD -> previousDays == 0 ? 3 : Math.max(previousDays + 2, previousDays * 2);
      case EASY -> previousDays == 0 ? 7 : Math.max(previousDays + 3, previousDays * 3);
      case AGAIN -> throw new IllegalStateException();
    };
    int limited = Math.min(days, 180);
    return new Interval(limited, now.plus(limited, ChronoUnit.DAYS));
  }
}
