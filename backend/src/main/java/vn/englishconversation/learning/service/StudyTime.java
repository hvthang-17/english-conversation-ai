package vn.englishconversation.learning.service;

import java.time.*;
import java.util.*;

public final class StudyTime {
  private StudyTime() {}

  public record Interval(Instant from, Instant to) {}

  public static NavigableMap<LocalDate, Long> byDay(List<Interval> intervals, ZoneId zone) {
    var result = new TreeMap<LocalDate, Long>();
    Interval current = null;
    for (var interval : intervals.stream().sorted(Comparator.comparing(Interval::from)).toList()) {
      if (!interval.from().isBefore(interval.to())) continue;
      if (current == null) current = interval;
      else if (interval.from().isAfter(current.to())) {
        add(result, current, zone);
        current = interval;
      } else if (interval.to().isAfter(current.to())) current = new Interval(current.from(), interval.to());
    }
    if (current != null) add(result, current, zone);
    return result;
  }

  private static void add(Map<LocalDate, Long> days, Interval interval, ZoneId zone) {
    Instant cursor = interval.from();
    while (cursor.isBefore(interval.to())) {
      LocalDate day = cursor.atZone(zone).toLocalDate();
      Instant midnight = day.plusDays(1).atStartOfDay(zone).toInstant();
      Instant end = midnight.isBefore(interval.to()) ? midnight : interval.to();
      days.merge(day, Duration.between(cursor, end).toMillis(), Long::sum);
      cursor = end;
    }
  }

  public static int streak(Map<LocalDate, Long> days, LocalDate today) {
    LocalDate day = qualified(days, today) ? today : today.minusDays(1);
    int count = 0;
    while (qualified(days, day)) {
      count++;
      day = day.minusDays(1);
    }
    return count;
  }

  public static boolean qualified(Map<LocalDate, Long> days, LocalDate day) {
    return days.getOrDefault(day, 0L) >= 60000;
  }
}
