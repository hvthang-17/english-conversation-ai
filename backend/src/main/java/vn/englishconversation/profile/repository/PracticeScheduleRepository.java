package vn.englishconversation.profile.repository;

import org.springframework.stereotype.Repository;
import vn.englishconversation.config.features.FeatureEnabled;
import vn.englishconversation.common.repository.JdbcStore;

/** Persistence only: caller retains transaction, lock ordering, claim/version and cleanup policy. */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class PracticeScheduleRepository {
  private final JdbcStore db;
  public PracticeScheduleRepository(JdbcStore db) { this.db = db; }
  public record Schedule(long id,String localTime,String timeZone,boolean enabled,boolean emailReminderEnabled) {}
  public record ScheduleDay(long dayOfWeek) {}
  public record ScheduleId(long id) {}
  public java.util.Optional<Schedule> schedule(long owner) {
    return db.find("SELECT * FROM practice_schedules WHERE user_id=?", owner).map(row -> new Schedule(row.number("id"), row.text("local_time"), row.text("time_zone"), row.flag("enabled"), row.flag("email_reminder_enabled")));
  }

  public java.util.List<ScheduleDay> days(long schedule) {
    return db.rows("SELECT day_of_week FROM practice_schedule_days WHERE schedule_id=? ORDER BY"
                    + " day_of_week", schedule).stream().map(row -> new ScheduleDay(row.number("day_of_week"))).toList();
  }

  public void saveSchedule(long owner,String time,String zone,boolean enabled,boolean email) {
    db.update("INSERT INTO"
            + " practice_schedules(user_id,local_time,time_zone,enabled,email_reminder_enabled,created_at,updated_at)"
            + " VALUES (?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE"
            + " local_time=VALUES(local_time),time_zone=VALUES(time_zone),enabled=VALUES(enabled),email_reminder_enabled=VALUES(email_reminder_enabled),version=version+1,updated_at=UTC_TIMESTAMP(6)", owner, time, zone, enabled, email);
  }

  public java.util.Optional<ScheduleId> scheduleId(long owner) {
    return db.find("SELECT id FROM practice_schedules WHERE user_id=?", owner).map(row -> new ScheduleId(row.number("id")));
  }

  public void clearDays(long schedule) {
    db.update("DELETE FROM practice_schedule_days WHERE schedule_id=?", schedule);
  }

  public void addDay(long schedule,int day) {
    db.update("INSERT INTO practice_schedule_days VALUES (?,?)", schedule, day);
  }

  public void cancelDeliveries(long schedule) {
    db.update("UPDATE practice_reminder_deliveries SET status='CANCELLED' WHERE schedule_id=? AND status"
            + " IN ('PENDING','RETRY','PROCESSING')", schedule);
  }
}
