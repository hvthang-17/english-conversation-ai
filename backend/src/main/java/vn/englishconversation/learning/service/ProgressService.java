package vn.englishconversation.learning.service;

import java.time.*;
import java.util.*;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.learning.repository.ProgressRepository.LessonState;
import vn.englishconversation.learning.repository.LearningStore;
import vn.englishconversation.learning.repository.ProgressRepository;

@Service
@FeatureEnabled(FeatureEnabled.Feature.PROGRESS)
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
      recordAssessment(owner, row.lessonId());
      result(owner, "listening:" + attempt, "Đề nghe đã được chấm", "Bạn có thể xem điểm và giải thích trong lịch sử luyện nghe.");
    });
  }

  public void pronunciationScored(long owner, long attempt) {
    progress.pronunciationLesson(owner, attempt).ifPresent(row -> recordAssessment(owner, row.lessonId()));
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
      var days = days(owner.id(), ZoneId.of(progress.profile(owner.id()).timeZone()));
      if (days.keySet().stream().anyMatch(day -> StudyTime.qualified(days, day)
          && StudyTime.qualified(days, day.minusDays(1)) && StudyTime.qualified(days, day.minusDays(2))))
        progress.badge(owner.id(), "THREE_DAY_STREAK");
    }
  }

  @Transactional(readOnly=true)
  public Dashboard dashboard(AccountPrincipal owner, String level) {
    var profile = progress.profile(owner.id());
    String selected = level == null ? profile.selectedLevel() : level;
    var zone = ZoneId.of(profile.timeZone());
    LocalDate today = LocalDate.now(zone);
    var days = days(owner.id(), zone);
    int streak = StudyTime.streak(days, today);
    long lessons = progress.completions(owner.id());
    Integer goal = profile.dailyGoalMinutes();
    return new Dashboard(selected, zone.getId(), goal, days.getOrDefault(today, 0L) / 1000,
        days.values().stream().mapToLong(Long::longValue).sum() / 1000, streak, lessons,
        progress.tasks(owner.id()), lessons*10, progress.badges(owner.id()),
        today.minusDays(13).datesUntil(today.plusDays(1)).map(day -> new Day(day, days.getOrDefault(day, 0L)/1000)).toList(),
        progress.topics(owner.id(), selected).stream().map(row ->
            new Topic(row.id(),row.name(),row.total(),row.completed())).toList(),
        progress.completionHistory(owner.id()).stream().map(row ->
            new CompletedLesson(row.id(),row.title(),row.available(),row.time())).toList(),
        progress.listening(owner.id()).stream().map(row -> new Score(row.id(),row.title(),
            row.level() + " · v" + row.gradingVersion() + " · " + row.questionFormat(),
            row.time(), row.score(), row.maxScore(), null)).toList(),
        progress.pronunciation(owner.id()).stream().map(row -> new Score(row.id(),
            "UNSCRIPTED".equals(row.mode()) ? "Hội thoại" : "SHADOWING".equals(row.drillMode()) ? "Nói đuổi" : "Đọc theo mẫu",
            row.mode() + " · " + Objects.toString(row.drillMode(),"") + " · " + row.locale()
                + " · " + row.provider() + " · " + row.assessmentVersion(),
            row.time(),null,null,row.overallScore())).toList(),
        progress.repeatedErrors(owner.id()).stream().map(row ->
            new ErrorGroup(row.ruleCode(),row.category(),row.sentences())).toList());
  }

  public List<Notification> notifications(AccountPrincipal owner, int page) {
    return progress.notifications(owner.id(), page).stream().map(row -> new Notification(row.id(),
        row.kind(),row.title(),row.body(),row.isRead(),row.time(),row.supportTicketId())).toList();
  }

  @Transactional
  public void read(AccountPrincipal owner, long id) {
    lock(owner);
    if (!progress.read(owner.id(), id)) throw new ApiException(404,"NOTIFICATION_NOT_FOUND","Không tìm thấy thông báo.");
  }

  private NavigableMap<LocalDate, Long> days(long owner, ZoneId zone) {
    return StudyTime.byDay(progress.intervals(owner).stream().map(row -> new StudyTime.Interval(
        Instant.parse(row.startedAt()), Instant.parse(row.endedAt()))).toList(), zone);
  }

  private void lock(AccountPrincipal owner) {
    if (!learning.lockAccount(owner.id(), owner.securityVersion()))
      throw new ApiException(401,"SESSION_REVOKED","Vui lòng đăng nhập lại.");
  }

  private static boolean canMark(LessonState lesson) { return !lesson.assessed() && lesson.hasMaterials(); }

  private LessonState visibleLesson(long id) {
    return progress.lesson(id).orElseThrow(() -> new ApiException(404,"LESSON_NOT_FOUND","Bài học không khả dụng."));
  }


}
