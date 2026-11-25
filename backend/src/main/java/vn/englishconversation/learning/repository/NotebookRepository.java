package vn.englishconversation.learning.repository;

import static vn.englishconversation.learning.dto.NotebookData.*;

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
    return card(owner, id).map(NotebookRepository::card).orElseThrow();
  }

  public Page<Attempt> history(long cardId, int page, int size) {
    long total = db.count("SELECT COUNT(*) FROM review_attempts WHERE review_item_id=?", cardId);
    var rows = db.rows(ATTEMPT + " WHERE review_item_id=? ORDER BY reviewed_at DESC,id DESC LIMIT ? OFFSET ?",
        cardId, size, (long) page * size);
    return new Page<>(rows.stream().map(NotebookRepository::attempt).toList(), page, size, total);
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

  public void delete(long owner, long cardId) {
    db.update("DELETE FROM review_items WHERE user_id=? AND id=?", owner, cardId);
  }

  public void clear(long owner) {
    db.update("DELETE FROM review_items WHERE user_id=?", owner);
  }

  private static Card card(Row row) {
    return new Card(row.text("id"), row.text("source_language_item_id"), row.text("item_type"),
        row.text("expression"), row.text("meaning_vi"), row.text("example_en"),
        row.text("ipa"), Status.valueOf(row.text("status")), row.text("last_reviewed"));
  }

  public static Attempt attempt(Row row) {
    return new Attempt(row.text("id"), Mode.valueOf(row.text("review_mode")), Status.valueOf(row.text("result")),
        row.text("is_correct") == null ? null : row.flag("is_correct"), row.text("reviewed"));
  }
}
