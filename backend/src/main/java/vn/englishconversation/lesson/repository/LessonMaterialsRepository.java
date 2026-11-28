package vn.englishconversation.lesson.repository;

import java.util.List;
import java.util.Optional;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;
import vn.englishconversation.lesson.dto.LessonMaterials.Item;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.MATERIALS)
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
            i.text("meaning_vi"), i.text("example_en"), i.text("example_vi"), i.text("saved_card_id"), i.text("part_of_speech")))
        .toList();
  }

  private List<Row> dialoguesRows(long lessonId) {
    return db.rows("""
        SELECT id,title,lines_json FROM sample_dialogues WHERE lesson_id=? AND status='ACTIVE'
        ORDER BY sort_order,id
        """, lessonId);
  }

  private Optional<Row> lockVisibleItemRows(long itemId) {
    return db.find("""
        SELECT i.* FROM language_items i
        JOIN lessons l ON l.id=i.lesson_id JOIN topics t ON t.id=l.topic_id
        WHERE i.id=? AND i.status='ACTIVE' AND
        """ + VISIBLE + " FOR SHARE", itemId);
  }


  public record DialogueSource(String id,String title,String linesJson) {}
  private static DialogueSource mapDialogueSource(Row row) { return new DialogueSource(row.text("id"),row.text("title"),row.text("lines_json")); }
  public java.util.List<DialogueSource> dialogues(long lessonId) { return dialoguesRows(lessonId).stream().map(LessonMaterialsRepository::mapDialogueSource).toList(); }

  public record MaterialSource(String kind,String expression,String meaningVi,String exampleEn,String ipa,String partOfSpeech) {}
  private static MaterialSource mapMaterialSource(Row row) { return new MaterialSource(row.text("kind"),row.text("expression"),row.text("meaning_vi"),row.text("example_en"),row.text("ipa"),row.text("part_of_speech")); }
  public java.util.Optional<MaterialSource> lockVisibleItem(long itemId) { return lockVisibleItemRows(itemId).map(LessonMaterialsRepository::mapMaterialSource); }
}
