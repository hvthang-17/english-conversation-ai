package vn.englishconversation.lesson.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;
import vn.englishconversation.lesson.dto.LessonMaterials.Item;

@Repository
@Profile("!bootstrap & !g1 & !g2")
public class LessonMaterialsRepository {
  private static final String ACTIVE_LESSON = """
      FROM lessons l JOIN topics t ON t.id=l.topic_id
      """;
  private static final String VISIBLE = "l.status='ACTIVE' AND t.is_active=TRUE";
  private final JdbcStore db;

  public LessonMaterialsRepository(JdbcStore db) {
    this.db = db;
  }

  public boolean visible(long lessonId) {
    return db.count("SELECT COUNT(*) " + ACTIVE_LESSON + " WHERE " + VISIBLE + " AND l.id=?", lessonId) > 0;
  }

  public List<Item> items(long lessonId, long ownerId) {
    return db.rows("""
        SELECT i.*,r.id AS saved_card_id
        FROM language_items i
        LEFT JOIN review_items r ON r.source_language_item_id=i.id AND r.user_id=?
        WHERE i.lesson_id=? AND i.status='ACTIVE'
        ORDER BY i.sort_order,i.id
        """, ownerId, lessonId).stream()
        .map(i -> new Item(i.text("id"), i.text("kind"), i.text("expression"), i.text("ipa"),
            i.text("meaning_vi"), i.text("example_en"), i.text("example_vi"), i.text("saved_card_id")))
        .toList();
  }

  public List<Row> dialogues(long lessonId) {
    return db.rows("""
        SELECT id,title,lines_json FROM sample_dialogues WHERE lesson_id=? AND status='ACTIVE'
        ORDER BY sort_order,id
        """, lessonId);
  }

  public Optional<Row> lockVisibleItem(long itemId) {
    return db.find("""
        SELECT i.kind,i.expression,i.meaning_vi,i.example_en,i.ipa FROM language_items i
        JOIN lessons l ON l.id=i.lesson_id JOIN topics t ON t.id=l.topic_id
        WHERE i.id=? AND i.status='ACTIVE' AND
        """ + VISIBLE + " FOR SHARE", itemId);
  }

  public List<String> quizMeanings(String kind, Long sourceId) {
    return db.rows("""
        SELECT i.meaning_vi FROM language_items i
        JOIN lessons l ON l.id=i.lesson_id JOIN topics t ON t.id=l.topic_id
        LEFT JOIN language_items source ON source.id=?
        WHERE i.kind=? AND i.status='ACTIVE' AND
        """ + VISIBLE + " ORDER BY (i.lesson_id=source.lesson_id) DESC, i.id LIMIT 40",
        sourceId, kind).stream().map(row -> row.text("meaning_vi")).toList();
  }
}
