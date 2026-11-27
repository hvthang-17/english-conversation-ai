package vn.englishconversation.learning;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.englishconversation.learning.service.StudyTime;
import vn.englishconversation.learning.service.StudyTime.Interval;

class StudyTimeTest {
  @Test
  void mergesOverlapsAndSplitsAtLocalMidnight() {
    var origin = Instant.parse("2026-11-26T16:59:00Z");
    var intervals = List.of(new Interval(origin, origin.plusSeconds(120)),
        new Interval(origin.plusSeconds(60),origin.plusSeconds(180)));
    var vn = StudyTime.byDay(intervals, ZoneId.of("Asia/Ho_Chi_Minh"));
    assertEquals(60000, vn.get(LocalDate.parse("2026-11-26")));
    assertEquals(120000, vn.get(LocalDate.parse("2026-11-27")));
    assertEquals(2, StudyTime.streak(vn, LocalDate.parse("2026-11-27")));
    var utc = StudyTime.byDay(intervals, ZoneOffset.UTC);
    assertEquals(180000,utc.get(LocalDate.parse("2026-11-26")));
    var shortDay = StudyTime.byDay(List.of(new Interval(origin,origin.plusSeconds(59))), ZoneOffset.UTC);
    assertEquals(0,StudyTime.streak(shortDay,LocalDate.parse("2026-11-26")));
  }
}
