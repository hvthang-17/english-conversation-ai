package vn.englishconversation.profile.service;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountLifecycleService;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.user.repository.UserRepository;

@Service
@Profile("!bootstrap")
public class PracticeScheduleService {
  public record Input(
      @NotNull @Size(max = 7) List<@NotNull @Min(1) @Max(7) Integer> days,
      @NotNull @Pattern(regexp = "[0-2][0-9]:[0-5][0-9]") String localTime,
      @NotBlank @Size(max = 64) String timeZone,
      boolean enabled,
      boolean emailReminderEnabled) {}

  public record View(
      List<Integer> days,
      String localTime,
      String timeZone,
      boolean enabled,
      boolean emailReminderEnabled,
      boolean emailReminderAvailable,
      Instant nextOccurrenceAt) {}

  private final JdbcStore db;
  private final UserRepository users;
  private final AccountLifecycleService lifecycle;
  private final boolean remindersEnabled;

  public PracticeScheduleService(
      JdbcStore db, UserRepository users, AccountLifecycleService lifecycle,
      @Value("${app.mail.reminders-enabled:true}") boolean remindersEnabled) {
    this.db = db;
    this.users = users;
    this.lifecycle = lifecycle;
    this.remindersEnabled = remindersEnabled;
  }

  @Transactional(readOnly = true)
  public View get(long owner) {
    var found = db.find("SELECT * FROM practice_schedules WHERE user_id=?", owner);
    if (found.isEmpty()) return null;
    var row = found.get();
    var days =
        db
            .rows(
                "SELECT day_of_week FROM practice_schedule_days WHERE schedule_id=? ORDER BY"
                    + " day_of_week",
                row.number("id"))
            .stream()
            .map(d -> (int) d.number("day_of_week"))
            .toList();
    String time = row.text("local_time").substring(0, 5);
    boolean enabled = row.flag("enabled");
    return new View(
        days,
        time,
        row.text("time_zone"),
        enabled,
        row.flag("email_reminder_enabled"),
        remindersEnabled && lifecycle.mailAvailable(),
        enabled
            ? next(days, LocalTime.parse(time), ZoneId.of(row.text("time_zone")), Instant.now())
            : null);
  }

  @Transactional
  public View save(AccountPrincipal owner, Input input) {
    var user =
        users
            .lockById(owner.id())
            .orElseThrow(() -> new ApiException(401, "SESSION_REVOKED", "Vui lòng đăng nhập lại."));
    if (!AccountService.valid(user, owner))
      throw new ApiException(401, "SESSION_REVOKED", "Vui lòng đăng nhập lại.");
    try {
      LocalTime.parse(input.localTime());
      ZoneId.of(input.timeZone());
    } catch (DateTimeException e) {
      throw new ApiException(400, "INVALID_SCHEDULE", "Giờ hoặc múi giờ không hợp lệ.");
    }
    if (new HashSet<>(input.days()).size() != input.days().size()
        || input.enabled() && input.days().isEmpty())
      throw new ApiException(400, "INVALID_SCHEDULE", "Chọn ngày luyện hợp lệ, không trùng nhau.");
    db.update(
        "INSERT INTO"
            + " practice_schedules(user_id,local_time,time_zone,enabled,email_reminder_enabled,created_at,updated_at)"
            + " VALUES (?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE"
            + " local_time=VALUES(local_time),time_zone=VALUES(time_zone),enabled=VALUES(enabled),email_reminder_enabled=VALUES(email_reminder_enabled),version=version+1,updated_at=UTC_TIMESTAMP(6)",
        owner.id(),
        input.localTime(),
        input.timeZone(),
        input.enabled(),
        input.emailReminderEnabled());
    long id =
        db.find("SELECT id FROM practice_schedules WHERE user_id=?", owner.id())
            .orElseThrow()
            .number("id");
    db.update("DELETE FROM practice_schedule_days WHERE schedule_id=?", id);
    for (int day : input.days())
      db.update("INSERT INTO practice_schedule_days VALUES (?,?)", id, day);
    db.update(
        "UPDATE practice_reminder_deliveries SET status='CANCELLED' WHERE schedule_id=? AND status"
            + " IN ('PENDING','RETRY','PROCESSING')",
        id);
    return get(owner.id());
  }

  public static Instant occurrence(LocalDate date, LocalTime time, ZoneId zone) {
    var local = date.atTime(time);
    var offsets = zone.getRules().getValidOffsets(local);
    return offsets.isEmpty() ? null : local.toInstant(offsets.getFirst());
  }

  public static Instant next(List<Integer> days, LocalTime time, ZoneId zone, Instant now) {
    var date = now.atZone(zone).toLocalDate();
    for (int n = 0; n < 15; n++) {
      var d = date.plusDays(n);
      if (!days.contains(d.getDayOfWeek().getValue())) continue;
      Instant at = occurrence(d, time, zone);
      if (at != null && at.isAfter(now)) return at;
    }
    return null;
  }
}
