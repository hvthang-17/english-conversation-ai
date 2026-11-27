package vn.englishconversation.learning.service;

import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.common.repository.JdbcStore.Row;
import vn.englishconversation.learning.repository.LearningStore;
import vn.englishconversation.learning.repository.ProgressRepository;

@Service
@Profile("g6")
public class ProgressService {
  public enum Activity { LESSON, LISTENING, PRONUNCIATION, CHAT, REVIEW }
  public record Completion(boolean completed, boolean canMark) {}
  public record Topic(String id, String name, long total, long completed) {}
  public record CompletedLesson(String id, String title, boolean available, String at) {}
  public record Day(LocalDate date, long seconds) {}
  public record Score(String id, String label, String group, String at, Integer score, Integer maximum,
                      java.math.BigDecimal pronunciationScore) {}
  public record ErrorGroup(String ruleCode, String category, long sentences) {}
  public record Notification(String id, String kind, String title, String body, boolean read, String at, String supportTicketId) {}
  public record Dashboard(String level, String timeZone, Integer goalMinutes, long todaySeconds,
      long totalSeconds, int streak, long lessons, long tasks, long points, List<String> badges,
      List<Day> recentDays, List<Topic> topics, List<CompletedLesson> recentCompletions, List<Score> listening, List<Score> pronunciation,
      List<ErrorGroup> repeatedErrors) {}

  private final ProgressRepository progress;
  private final LearningStore learning;
  public ProgressService(ProgressRepository progress, LearningStore learning) {
    this.progress = progress;
    this.learning = learning;
  }

  @Transactional
  public Completion completion(AccountPrincipal owner, long id) {
    var lesson = visibleLesson(id);
    return new Completion(progress.completed(owner.id(), id), canMark(lesson));
  }

  @Transactional
  public Completion mark(AccountPrincipal owner, long id) {
    lock(owner);
    var lesson = visibleLesson(id);
    if (!canMark(lesson) && !progress.completed(owner.id(), id))
      throw new ApiException(422, "LESSON_ASSESSMENT_REQUIRED", "Hãy học bài có học liệu hoặc hoàn tất đề nghe hay bài phát âm của bài.");
    award(owner.id(), id);
    return new Completion(true, canMark(lesson));
  }

  public void listeningSubmitted(long owner, long attempt) {
    progress.listeningLesson(owner, attempt).ifPresent(row -> {
      recordAssessment(owner, row.number("lesson_id"));
      result(owner, "listening:" + attempt, "Đề nghe đã được chấm", "Bạn có thể xem điểm và giải thích trong lịch sử luyện nghe.");
    });
  }

  public void pronunciationScored(long owner, long attempt) {
    progress.pronunciationLesson(owner, attempt).ifPresent(row -> recordAssessment(owner, row.number("lesson_id")));
    result(owner, "pronunciation:" + attempt, "Đã có điểm phát âm", "Bạn có thể xem kết quả trong lần luyện tương ứng.");
  }

  private void result(long owner, String key, String title, String body) { progress.notify(owner, "RESULT", key, title, body); }

  private void recordAssessment(long owner, long lesson) {
    if (progress.lesson(lesson).isPresent()) award(owner, lesson);
  }

  private void award(long owner, long lesson) {
    progress.complete(owner, lesson);
    long total = progress.completions(owner);
    if (total >= 1) progress.badge(owner, "FIRST_LESSON");
    if (total >= 5) progress.badge(owner, "FIVE_LESSONS");
  }

  @Transactional
  public void heartbeat(AccountPrincipal owner, UUID event, UUID stream, Activity kind) {
    lock(owner);
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    Instant start = progress.previous(owner.id(), stream.toString())
        .filter(last -> last.isBefore(now) && !last.isBefore(now.minusSeconds(45))).orElse(now);
    if (start.isBefore(now.minusSeconds(30))) start = now.minusSeconds(30);
    progress.interval(owner.id(), event.toString(), stream.toString(), kind.name(), start, now);
    if (!progress.badges(owner.id()).contains("THREE_DAY_STREAK")) {
      var days = days(owner.id(), ZoneId.of(progress.profile(owner.id()).text("time_zone")));
      if (days.keySet().stream().anyMatch(day -> StudyTime.qualified(days, day)
          && StudyTime.qualified(days, day.minusDays(1)) && StudyTime.qualified(days, day.minusDays(2))))
        progress.badge(owner.id(), "THREE_DAY_STREAK");
    }
  }

  @Transactional(readOnly=true)
  public Dashboard dashboard(AccountPrincipal owner, String level) {
    var profile = progress.profile(owner.id());
    String selected = level == null ? profile.text("selected_level") : level;
    var zone = ZoneId.of(profile.text("time_zone"));
    LocalDate today = LocalDate.now(zone);
    var days = days(owner.id(), zone);
    int streak = StudyTime.streak(days, today);
    long lessons = progress.completions(owner.id());
    Integer goal = integer(profile, "daily_goal_minutes");
    return new Dashboard(selected, zone.getId(), goal, days.getOrDefault(today, 0L) / 1000,
        days.values().stream().mapToLong(Long::longValue).sum() / 1000, streak, lessons,
        progress.tasks(owner.id()), lessons*10, progress.badges(owner.id()),
        today.minusDays(13).datesUntil(today.plusDays(1)).map(day -> new Day(day, days.getOrDefault(day, 0L)/1000)).toList(),
        progress.topics(owner.id(), selected).stream().map(row ->
            new Topic(row.text("id"),row.text("name"),row.number("total"),row.number("completed"))).toList(),
        progress.completionHistory(owner.id()).stream().map(row ->
            new CompletedLesson(row.text("id"),row.text("title"),row.flag("available"),row.text("time"))).toList(),
        progress.listening(owner.id()).stream().map(row -> new Score(row.text("id"),row.text("title"),
            row.text("level") + " · v" + row.text("grading_version") + " · " + row.text("question_format"),
            row.text("time"), integer(row,"score"), integer(row,"max_score"), null)).toList(),
        progress.pronunciation(owner.id()).stream().map(row -> new Score(row.text("id"),
            "UNSCRIPTED".equals(row.text("mode")) ? "Hội thoại" : "SHADOWING".equals(row.text("drill_mode")) ? "Nói đuổi" : "Đọc theo mẫu",
            row.text("mode") + " · " + Objects.toString(row.text("drill_mode"),"") + " · " + row.text("locale")
                + " · " + row.text("provider") + " · " + row.text("assessment_version"),
            row.text("time"),null,null,(java.math.BigDecimal) row.data().get("overall_score"))).toList(),
        progress.repeatedErrors(owner.id()).stream().map(row ->
            new ErrorGroup(row.text("rule_code"),row.text("category"),row.number("sentences"))).toList());
  }

  public List<Notification> notifications(AccountPrincipal owner, int page) {
    return progress.notifications(owner.id(), page).stream().map(row -> new Notification(row.text("id"),
        row.text("kind"),row.text("title"),row.text("body"),row.flag("is_read"),row.text("time"),row.text("support_ticket_id"))).toList();
  }

  @Transactional
  public void read(AccountPrincipal owner, long id) {
    lock(owner);
    if (!progress.read(owner.id(), id)) throw new ApiException(404,"NOTIFICATION_NOT_FOUND","Không tìm thấy thông báo.");
  }

  private NavigableMap<LocalDate, Long> days(long owner, ZoneId zone) {
    return StudyTime.byDay(progress.intervals(owner).stream().map(row -> new StudyTime.Interval(
        Instant.parse(row.text("started_at")), Instant.parse(row.text("ended_at")))).toList(), zone);
  }

  private void lock(AccountPrincipal owner) {
    if (!learning.lockAccount(owner.id(), owner.securityVersion()))
      throw new ApiException(401,"SESSION_REVOKED","Vui lòng đăng nhập lại.");
  }

  private static boolean canMark(Row lesson) { return !lesson.flag("assessed") && lesson.flag("has_materials"); }

  private Row visibleLesson(long id) {
    return progress.lesson(id).orElseThrow(() -> new ApiException(404,"LESSON_NOT_FOUND","Bài học không khả dụng."));
  }

  private static Integer integer(Row row, String key) {
    return row.data().get(key) instanceof Number number ? number.intValue() : null;
  }
}
