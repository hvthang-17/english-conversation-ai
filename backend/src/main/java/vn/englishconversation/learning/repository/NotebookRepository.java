package vn.englishconversation.learning.repository;

import static vn.englishconversation.learning.dto.NotebookData.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;

@Repository
@Profile("!bootstrap & !g1 & !g2")
public class NotebookRepository {
  private static final String CARD = """
      SELECT *, DATE_FORMAT(last_reviewed_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS last_reviewed
      FROM review_items
      """;
  private static final String ATTEMPT = """
      SELECT *, DATE_FORMAT(reviewed_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS reviewed
      FROM review_attempts
      """;
  private final JdbcStore db;

  public NotebookRepository(JdbcStore db) {
    this.db = db;
  }

  public Page<Card> cards(long ownerId, Status status, int page, int size) {
    String where = " WHERE user_id=?";
    if (status != null) where += " AND status=?";
    Object[] filters = status == null ? new Object[] {ownerId} : new Object[] {ownerId, status.name()};
    long total = db.count("SELECT COUNT(*) FROM review_items" + where, filters);
    var rows = status == null
        ? db.rows(CARD + where + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", ownerId, size, (long) page * size)
        : db.rows(CARD + where + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", ownerId, status.name(), size, (long) page * size);
    return new Page<>(rows.stream().map(NotebookRepository::card).toList(), page, size, total);
  }

  public Optional<Row> card(long owner, long cardId) {
    return db.find(CARD + " WHERE user_id=? AND id=?", owner, cardId);
  }

  public String timeZone(long owner) {
    return db.find("SELECT time_zone FROM practice_schedules WHERE user_id=?", owner)
        .map(row -> row.text("time_zone")).orElse("Asia/Ho_Chi_Minh");
  }

  public long firstReviews(long owner, LocalDateTime start, LocalDateTime end) {
    return db.count("""
        SELECT COUNT(*) FROM review_items
        WHERE user_id=? AND first_spaced_reviewed_at>=? AND first_spaced_reviewed_at<?
        """, owner, start, end);
  }

  public long newCards(long owner) {
    return db.count("SELECT COUNT(*) FROM review_items WHERE user_id=? AND first_spaced_reviewed_at IS NULL", owner);
  }

  public long dueCards(long owner, LocalDateTime now) {
    return db.count("""
        SELECT COUNT(*) FROM review_items WHERE user_id=? AND next_review_at<=? AND schedule_version>0
        """, owner, now);
  }

  public String nextDue(long owner, LocalDateTime now) {
    return db.find("""
        SELECT DATE_FORMAT(next_review_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS due
        FROM review_items WHERE user_id=? AND next_review_at>? AND schedule_version>0 ORDER BY next_review_at,id LIMIT 1
        """, owner, now).map(row -> row.text("due")).orElse(null);
  }

  public List<ScheduledCard> due(long owner, LocalDateTime now, int limit) {
    return db.rows(scheduledSelect() + " WHERE user_id=? AND next_review_at<=? AND schedule_version>0 ORDER BY next_review_at,id LIMIT ?",
        owner, now, limit).stream().map(NotebookRepository::scheduled).toList();
  }

  public List<ScheduledCard> newQueue(long owner, int limit) {
    return db.rows(scheduledSelect() + """
         WHERE user_id=? AND first_spaced_reviewed_at IS NULL ORDER BY created_at,id LIMIT ?
        """, owner, limit).stream().map(NotebookRepository::scheduled).toList();
  }

  private static String scheduledSelect() {
    return """
        SELECT *, DATE_FORMAT(last_reviewed_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS last_reviewed,
          DATE_FORMAT(next_review_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS due
        FROM review_items
        """;
  }

  private static ScheduledCard scheduled(Row row) {
    long version = row.number("schedule_version");
    return new ScheduledCard(card(row), version == 0 ? null : row.text("due"), version);
  }

  public Optional<Card> saved(long owner, long sourceId) {
    return db.find(CARD + " WHERE user_id=? AND source_language_item_id=?", owner, sourceId)
        .map(NotebookRepository::card);
  }

  public Card save(long owner, long sourceId, Row material) {
    long id = db.insert("""
        INSERT INTO review_items
          (user_id,source_language_item_id,item_type,expression,meaning_vi,example_en,ipa,created_at,updated_at)
        VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """, owner, sourceId, "WORD".equals(material.text("kind")) ? "VOCABULARY" : "PHRASE",
        material.text("expression"), material.text("meaning_vi"), material.text("example_en"), material.text("ipa"));
    String partOfSpeech = material.text("part_of_speech");
    if (partOfSpeech != null)
      db.update("UPDATE review_items SET part_of_speech=? WHERE id=?", partOfSpeech, id);
    return card(owner, id).map(NotebookRepository::card).orElseThrow();
  }

  public Optional<Row> prior(long cardId, String key) {
    return db.find(ATTEMPT + " WHERE review_item_id=? AND client_request_id=?", cardId, key);
  }

  public Attempt addAttempt(long cardId, ReviewInput input, Status result, Boolean correct, String fingerprint) {
    long id = db.insert("""
        INSERT INTO review_attempts
          (review_item_id,review_mode,result,client_request_id,request_fingerprint,reviewed_at,is_correct)
        VALUES (?,?,?,?,?,UTC_TIMESTAMP(6),?)
        """, cardId, input.mode().name(), result.name(), input.clientRequestId(), fingerprint, correct);
    db.update("""
        UPDATE review_items r JOIN review_attempts a ON a.review_item_id=r.id
        SET r.status=IF(a.review_mode='FLASHCARD',a.result,r.status),
            r.last_reviewed_at=a.reviewed_at,r.updated_at=a.reviewed_at
        WHERE a.id=?
        """, id);
    return db.find(ATTEMPT + " WHERE id=?", id).map(NotebookRepository::attempt).orElseThrow();
  }

  public Attempt addScheduledAttempt(long cardId, ReviewInput input, Status result, String fingerprint,
      String algorithm, int days, LocalDateTime now, LocalDateTime due) {
    long version = input.scheduleVersion() + 1;
    long id = db.insert("""
        INSERT INTO review_attempts
          (review_item_id,review_mode,result,client_request_id,request_fingerprint,reviewed_at,
           rating,algorithm_version,schedule_version_after,interval_days_after,next_review_at_after)
        VALUES (?,'SPACED_REVIEW',?,?,?,?,?,?,?,?,?)
        """, cardId, result.name(), input.clientRequestId(), fingerprint, now,
        input.rating().name(), algorithm, version, days, due);
    db.update("""
        UPDATE review_items
        SET schedule_version=?,spaced_interval_days=?,next_review_at=?,
            first_spaced_reviewed_at=COALESCE(first_spaced_reviewed_at,?),
            last_reviewed_at=?,updated_at=? WHERE id=?
        """, version, days, due, now, now, now, cardId);
    return db.find(ATTEMPT + " WHERE id=?", id).map(NotebookRepository::attempt).orElseThrow();
  }

  public void delete(long owner, long cardId) {
    db.update("DELETE FROM review_items WHERE user_id=? AND id=?", owner, cardId);
  }

  public void clear(long owner) {
    db.update("DELETE FROM review_items WHERE user_id=?", owner);
  }

  public static Card card(Row row) {
    return new Card(row.text("id"), row.text("source_language_item_id"), row.text("item_type"),
        row.text("expression"), row.text("meaning_vi"), row.text("example_en"),
        row.text("ipa"), Status.valueOf(row.text("status")), row.text("last_reviewed"), row.text("part_of_speech"));
  }

  public static Attempt attempt(Row row) {
    Mode mode = Mode.valueOf(row.text("review_mode"));
    boolean scheduled = mode == Mode.SPACED_REVIEW;
    return new Attempt(row.text("id"), mode, Status.valueOf(row.text("result")),
        row.text("is_correct") == null ? null : row.flag("is_correct"), row.text("reviewed"),
        scheduled ? Rating.valueOf(row.text("rating")) : null, row.text("algorithm_version"),
        scheduled ? row.number("schedule_version_after") : null,
        scheduled ? (int) row.number("interval_days_after") : null,
        scheduled ? ((LocalDateTime) row.data().get("next_review_at_after")).toInstant(ZoneOffset.UTC).toString() : null);
  }
}
