package vn.englishconversation.admin.repository;

import java.util.*;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.admin.dto.MaterialKind;
import vn.englishconversation.admin.dto.LessonCatalogOptions;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;

/** SQL uses only the closed MaterialKind catalog; transaction/lock order belongs to the caller. */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.ADMIN)
public class MaterialAdminRepository {
  private final JdbcStore db;
  public MaterialAdminRepository(JdbcStore db) { this.db = db; }
  public sealed interface Content permits TopicContent, LessonContent, WordContent, DialogueContent, ListeningContent, PronunciationContent {}
  public record MaterialState(long id, String status, Content content) {}
  public record LessonVisibility(String status, boolean activeTopic) {}
  public record TopicContent(String code, String name, String description, Long sortOrder) implements Content {}
  public record LessonContent(Long topicId, String level, Long scenarioId, String title, String summary, String objectiveCode, String learningObjective) implements Content {}
  public record WordContent(Long lessonId, String kind, String expression, String ipa, String meaningVi, String exampleEn, String exampleVi, String partOfSpeech, Long sortOrder) implements Content {}
  public record DialogueContent(Long lessonId, String title, String linesJson, Long sortOrder) implements Content {}
  public record ListeningContent(Long lessonId, String title, String instructions, String ttsVoiceKey, String transcriptEn, String translationVi, String scriptSegmentsJson, String questionsJson, Long sortOrder) implements Content {}
  public record PronunciationContent(Long lessonId, String referenceText, String locale, String mode, Long sortOrder) implements Content {}
  private static Content content(MaterialKind kind, Row row) { return switch (kind) {
    case TOPICS -> new TopicContent(row.text("code"),row.text("name"),row.text("description"),nullableLong(row,"sort_order"));
    case LESSONS -> new LessonContent(nullableLong(row,"topic_id"),row.text("level"),nullableLong(row,"scenario_id"),row.text("title"),row.text("summary"),row.text("objective_code"),row.text("learning_objective"));
    case WORDS -> new WordContent(nullableLong(row,"lesson_id"),row.text("kind"),row.text("expression"),row.text("ipa"),row.text("meaning_vi"),row.text("example_en"),row.text("example_vi"),row.text("part_of_speech"),nullableLong(row,"sort_order"));
    case DIALOGUES -> new DialogueContent(nullableLong(row,"lesson_id"),row.text("title"),row.text("lines_json"),nullableLong(row,"sort_order"));
    case LISTENING -> new ListeningContent(nullableLong(row,"lesson_id"),row.text("title"),row.text("instructions"),row.text("tts_voice_key"),row.text("transcript_en"),row.text("translation_vi"),row.text("script_segments_json"),row.text("questions_json"),nullableLong(row,"sort_order"));
    case PRONUNCIATION -> new PronunciationContent(nullableLong(row,"lesson_id"),row.text("reference_text"),row.text("locale"),row.text("mode"),nullableLong(row,"sort_order"));
  }; }
  private static Long nullableLong(Row row,String field) { return row.data().get(field) instanceof Number n ? n.longValue() : null; }
  private static MaterialState map(MaterialKind kind, Row row) {
    String status = kind == MaterialKind.TOPICS ? (row.flag("is_active") ? "ACTIVE" : row.flag("admin_draft") ? "DRAFT" : "ARCHIVED") : row.text("status");
    return new MaterialState(row.number("id"),status,content(kind,row));
  }
  public LessonCatalogOptions lessonCatalogOptions() {
    var topics = db.rows("SELECT id,name,is_active FROM topics ORDER BY sort_order,name,id").stream()
        .map(row -> new LessonCatalogOptions.Topic(row.text("id"), row.text("name"), row.flag("is_active")))
        .toList();
    var scenarios = db.rows("""
        SELECT s.id,s.title,s.topic_id,t.name AS topic_name,(s.status='ACTIVE' AND t.is_active) AS visible,
           (SELECT GROUP_CONCAT(l.level ORDER BY l.level) FROM scenario_levels l WHERE l.scenario_id=s.id) AS levels
        FROM scenarios s JOIN topics t ON t.id=s.topic_id
        ORDER BY s.title,s.id
        """).stream().map(row -> new LessonCatalogOptions.Scenario(row.text("id"), row.text("title"),
        row.text("topic_id"), row.text("topic_name"), row.flag("visible"),
        row.text("levels") == null ? List.of() : List.of(row.text("levels").split(",")))).toList();
    var lessons = db.rows("""
        SELECT l.id,l.title,l.topic_id,l.level,(l.status='ACTIVE' AND t.is_active) AS visible
        FROM lessons l JOIN topics t ON t.id=l.topic_id ORDER BY l.title,l.id
        """).stream().map(row -> new LessonCatalogOptions.Lesson(row.text("id"), row.text("title"),
        row.text("topic_id"), row.text("level"), row.flag("visible"))).toList();
    return new LessonCatalogOptions(topics, scenarios, lessons);
  }

  public List<MaterialState> list(MaterialKind kind,int page,int size,String expressionKind) {
    var rows = expressionKind == null
        ? db.rows("SELECT * FROM " + kind.table + " ORDER BY id DESC LIMIT ? OFFSET ?",size,(long)page*size)
        : db.rows("SELECT * FROM " + kind.table + " WHERE kind=? ORDER BY id DESC LIMIT ? OFFSET ?",expressionKind,size,(long)page*size);
    return rows.stream().map(row -> map(kind,row)).toList();
  }
  public long count(MaterialKind kind,String expressionKind) {
    return expressionKind == null ? db.count("SELECT COUNT(*) FROM " + kind.table)
        : db.count("SELECT COUNT(*) FROM " + kind.table + " WHERE kind=?",expressionKind);
  }
  /** FOR UPDATE must run in the caller's write transaction. */
  public Optional<MaterialState> find(MaterialKind kind,long id,boolean lock) {
    return db.find("SELECT * FROM " + kind.table + " WHERE id=?" + (lock ? " FOR UPDATE" : ""),id).map(row -> map(kind,row));
  }
  public long save(Long id,Content content) {
    MaterialKind kind = switch(content) {
      case TopicContent value -> MaterialKind.TOPICS;
      case LessonContent value -> MaterialKind.LESSONS;
      case WordContent value -> MaterialKind.WORDS;
      case DialogueContent value -> MaterialKind.DIALOGUES;
      case ListeningContent value -> MaterialKind.LISTENING;
      case PronunciationContent value -> MaterialKind.PRONUNCIATION;
    };
    Object[] fields = switch(content) {
      case TopicContent value -> new Object[] {value.code(),value.name(),value.description(),value.sortOrder()};
      case LessonContent value -> new Object[] {value.topicId(),value.level(),value.scenarioId(),value.title(),value.summary(),value.objectiveCode(),value.learningObjective()};
      case WordContent value -> new Object[] {value.lessonId(),value.kind(),value.expression(),value.ipa(),value.meaningVi(),value.exampleEn(),value.exampleVi(),value.partOfSpeech(),value.sortOrder()};
      case DialogueContent value -> new Object[] {value.lessonId(),value.title(),value.linesJson(),value.sortOrder()};
      case ListeningContent value -> new Object[] {value.lessonId(),value.title(),value.instructions(),value.ttsVoiceKey(),value.transcriptEn(),value.translationVi(),value.scriptSegmentsJson(),value.questionsJson(),value.sortOrder()};
      case PronunciationContent value -> new Object[] {value.lessonId(),value.referenceText(),value.locale(),value.mode(),value.sortOrder()};
    };
    String assignments=String.join("=?,",kind.fields)+"=?,updated_at=UTC_TIMESTAMP(6)";
    if(id==null) {
      String draft=kind==MaterialKind.TOPICS ? "is_active=FALSE,admin_draft=TRUE" : "status='DRAFT'";
      return db.insert("INSERT INTO "+kind.table+" SET "+assignments+",created_at=UTC_TIMESTAMP(6),"+draft,fields);
    }
    var values=new ArrayList<>(Arrays.asList(fields)); values.add(id);
    db.update("UPDATE "+kind.table+" SET "+assignments+" WHERE id=?",values.toArray());
    return id;
  }
  public void change(MaterialKind kind,long id,boolean publish) {
    if(kind==MaterialKind.TOPICS) db.update("UPDATE topics SET is_active=?,admin_draft=FALSE,updated_at=UTC_TIMESTAMP(6) WHERE id=?",publish,id);
    else db.update("UPDATE "+kind.table+" SET status=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",publish ? "ACTIVE" : "ARCHIVED",id);
  }
  public boolean referenced(MaterialKind kind,long id) {
    for(String reference:kind.references) {
      var pair=reference.split(":");
      if(db.count("SELECT COUNT(*) FROM "+pair[0]+" WHERE "+pair[1]+"=?",id)!=0) return true;
    }
    return false;
  }
  public void delete(MaterialKind kind,long id) { db.update("DELETE FROM "+kind.table+" WHERE id=?",id); }
  public Optional<LessonVisibility> lesson(Long id,boolean lock) {
    return db.find("SELECT l.status,t.is_active FROM lessons l JOIN topics t ON t.id=l.topic_id WHERE l.id=?"+(lock ? " FOR SHARE" : ""),id)
        .map(row -> new LessonVisibility(row.text("status"),row.flag("is_active")));
  }
  public Optional<Boolean> topic(Long id,boolean lock) {
    return db.find("SELECT is_active FROM topics WHERE id=?"+(lock ? " FOR SHARE" : ""),id).map(row -> row.flag("is_active"));
  }
  public boolean scenarioSupports(long id,String level,boolean publish) {
    return db.count("""
        SELECT COUNT(*) FROM scenario_levels sl JOIN scenarios s ON s.id=sl.scenario_id
        JOIN topics t ON t.id=s.topic_id WHERE sl.scenario_id=? AND sl.level=?
        """+(publish ? " AND s.status='ACTIVE' AND t.is_active=TRUE" : ""),id,level)!=0;
  }
}
