package vn.englishconversation.profile.service;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.mail.provider.MailGateway;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.profile.repository.PracticeScheduleRepository;
import vn.englishconversation.user.repository.UserRepository;

@Service
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class PracticeScheduleService {
  private final PracticeScheduleRepository persistence;
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

  private final UserRepository users;
  private final MailGateway mail;
  private final boolean remindersEnabled;

  public PracticeScheduleService(PracticeScheduleRepository persistence,
      UserRepository users, MailGateway mail,
      @Value("${app.mail.reminders-enabled:false}") boolean remindersEnabled) {
    this.persistence = persistence;
    this.users = users;
    this.mail = mail;
    this.remindersEnabled = remindersEnabled;
  }

  @Transactional(readOnly = true)
  public View get(long owner) {
    var found = persistence.schedule(owner);
    if (found.isEmpty()) return null;
    var row = found.get();
    var days =
        persistence.days(row.id())
            .stream()
            .map(d -> (int) d.dayOfWeek())
            .toList();
    String time = row.localTime().substring(0, 5);
    boolean enabled = row.enabled();
    return new View(
        days,
        time,
        row.timeZone(),
        enabled,
        row.emailReminderEnabled(),
        remindersEnabled && mail.available(),
        enabled
            ? next(days, LocalTime.parse(time), ZoneId.of(row.timeZone()), Instant.now())
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
    var days = input.days().stream().sorted().distinct().toList();
    if (days.size() != input.days().size() || input.enabled() && days.isEmpty())
      throw new ApiException(400, "INVALID_SCHEDULE", "Chọn ngày luyện hợp lệ, không trùng nhau.");
    View current = get(owner.id());
    if (current != null && current.days().equals(days)
        && current.localTime().equals(input.localTime()) && current.timeZone().equals(input.timeZone())
        && current.enabled() == input.enabled()
        && current.emailReminderEnabled() == input.emailReminderEnabled())
      return current;
    persistence.saveSchedule(owner.id(), input.localTime(), input.timeZone(), input.enabled(), input.emailReminderEnabled());
    long id =
        persistence.scheduleId(owner.id())
            .orElseThrow()
            .id();
    persistence.clearDays(id);
    for (int day : days)
      persistence.addDay(id, day);
    persistence.cancelDeliveries(id);
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
