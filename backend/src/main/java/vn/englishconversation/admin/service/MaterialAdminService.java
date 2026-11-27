package vn.englishconversation.admin.service;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.admin.dto.AdminPage;
import vn.englishconversation.admin.dto.MaterialKind;
import vn.englishconversation.admin.dto.LessonCatalogOptions;
import vn.englishconversation.admin.repository.AdminAudit;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;
import vn.englishconversation.lesson.dto.LessonMaterials.Line;
import vn.englishconversation.lesson.service.SampleDialogueFormat;
import vn.englishconversation.listening.dto.ListeningData.Question;
import vn.englishconversation.listening.dto.ListeningData.Segment;
import vn.englishconversation.listening.service.ListeningRules;

@Service
@Profile("g6")
public class MaterialAdminService {
  private final JdbcStore db;
  private final AccountService accounts;
  private final ObjectMapper json;
  private final AdminAudit audit;

  public MaterialAdminService(JdbcStore db, AccountService accounts, ObjectMapper json, AdminAudit audit) {
    this.db = db;
    this.accounts = accounts;
    this.json = json;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
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

  public AdminPage<Map<String, Object>> list(MaterialKind kind, int page, int size) {
    return list(kind, page, size, null);
  }

  public AdminPage<Map<String, Object>> list(MaterialKind kind, int page, int size, String expressionKind) {
    if (page < 0 || size < 1 || size > 50) throw invalid("Trang hoặc kích thước trang không hợp lệ.");
    if (expressionKind != null) {
      if (kind != MaterialKind.WORDS || !Set.of("WORD", "PHRASE").contains(expressionKind))
        throw invalid("Loại từ hoặc mẫu câu không hợp lệ.");
      return new AdminPage<>(db.rows("SELECT * FROM " + kind.table + " WHERE kind=? ORDER BY id DESC LIMIT ? OFFSET ?",
          expressionKind, size, (long) page * size).stream().map(row -> view(kind, row)).toList(),
          page, size, db.count("SELECT COUNT(*) FROM " + kind.table + " WHERE kind=?", expressionKind));
    }
    return new AdminPage<>(db.rows("SELECT * FROM " + kind.table + " ORDER BY id DESC LIMIT ? OFFSET ?",
        size, (long) page * size).stream().map(row -> view(kind, row)).toList(),
        page, size, db.count("SELECT COUNT(*) FROM " + kind.table));
  }

  @Transactional
  public Map<String, Object> save(AccountPrincipal actor, MaterialKind kind, Long id, Map<String, JsonNode> input) {
    accounts.requireAdminForWrite(actor);
    Row previous = id == null ? null : row(kind, id, true);
    if (previous != null && kind != MaterialKind.TOPICS) requireDraft(kind, previous);
    if (!kind.fields.containsAll(input.keySet())) throw invalid("Có trường ngoài hợp đồng học liệu.");
    var fields = new LinkedHashMap<String, Object>();
    for (String field : kind.fields) {
      JsonNode value = input.get(field);
      Object parsed = null;
      if (value != null && !value.isNull()) {
        if (field.endsWith("_id") || field.equals("sort_order")) parsed = number(field, value);
        else if (field.endsWith("_json") && value.isArray()) parsed = value.toString();
        else if (!field.endsWith("_json") && value.isString()) parsed = value.asString().strip();
        else throw invalid("Sai kiểu dữ liệu của " + field + ".");
      }
      fields.put(field, parsed);
    }
    validate(kind, fields, false);
    if (previous != null && kind == MaterialKind.TOPICS && !status(kind, previous).equals("DRAFT")
        && !previous.text("code").equals(fields.get("code")))
      throw conflict("Mã chủ đề đã phát hành cần được giữ để bảo toàn các tham chiếu.");
    boolean creating = id == null;
    String assignments = String.join("=?,", kind.fields) + "=?,updated_at=UTC_TIMESTAMP(6)";
    try {
      if (id == null) {
        String draft = kind == MaterialKind.TOPICS ? "is_active=FALSE,admin_draft=TRUE" : "status='DRAFT'";
        id = db.insert("INSERT INTO " + kind.table + " SET " + assignments + ",created_at=UTC_TIMESTAMP(6)," + draft,
            fields.values().toArray());
      } else {
        var values = new ArrayList<>(fields.values());
        values.add(id);
        db.update("UPDATE " + kind.table + " SET " + assignments + " WHERE id=?", values.toArray());
      }
    } catch (DataIntegrityViolationException conflict) {
      throw conflict("Mã đã tồn tại hoặc tham chiếu học liệu không hợp lệ.");
    }
    if (kind == MaterialKind.TOPICS) audit.catalog(actor.id(), "TOPIC", id, creating);
    return view(kind, row(kind, id, false));
  }

  @Transactional
  public void change(AccountPrincipal actor, MaterialKind kind, long id, boolean publish) {
    accounts.requireAdminForWrite(actor);
    var item = row(kind, id, true);
    if (publish) {
      if (kind == MaterialKind.LISTENING)
        throw conflict("Hãy công bố đề qua API tạo MP3 của Azure.");
      if (status(kind, item).equals("ACTIVE")) return;
      validate(kind, item.data(), true);
    } else if (!status(kind, item).equals("ACTIVE")) {
      throw conflict("Chỉ ẩn nội dung đang công khai.");
    }
    if (kind == MaterialKind.TOPICS) {
      db.update("UPDATE topics SET is_active=?,admin_draft=FALSE,updated_at=UTC_TIMESTAMP(6) WHERE id=?", publish, id);
      audit.catalog(actor.id(), "TOPIC", id, false);
    } else {
      db.update("UPDATE " + kind.table + " SET status=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",
          publish ? "ACTIVE" : "ARCHIVED", id);
    }
  }

  @Transactional
  public void deleteDraft(AccountPrincipal actor, MaterialKind kind, long id) {
    accounts.requireAdminForWrite(actor);
    requireDraft(kind, row(kind, id, true));
    for (String reference : kind.references) {
      var pair = reference.split(":");
      if (db.count("SELECT COUNT(*) FROM " + pair[0] + " WHERE " + pair[1] + "=?", id) != 0)
        throw conflict("Bản nháp có dữ liệu tham chiếu.");
    }
    try {
      db.update("DELETE FROM " + kind.table + " WHERE id=?", id);
      if (kind == MaterialKind.TOPICS) audit.catalogDeleted(actor.id(), "TOPIC", id);
    } catch (DataIntegrityViolationException conflict) {
      throw conflict("Bản nháp có dữ liệu tham chiếu.");
    }
  }

  private void validate(MaterialKind kind, Map<String, Object> data, boolean publish) {
    if (kind.fields.contains("sort_order")
        && (!(data.get("sort_order") instanceof Number order) || order.longValue() < (kind == MaterialKind.TOPICS ? 0 : 1)
          || order.longValue() > Integer.MAX_VALUE)) throw invalid("Thứ tự hiển thị không hợp lệ.");
    if (kind.fields.contains("lesson_id")) {
      var lesson = db.find("SELECT l.status,t.is_active FROM lessons l JOIN topics t ON t.id=l.topic_id WHERE l.id=?" + (publish ? " FOR SHARE" : ""),
          data.get("lesson_id")).orElseThrow(() -> invalid("Bài học không tồn tại."));
      if (publish && (!lesson.flag("is_active") || !"ACTIVE".equals(lesson.text("status"))))
        throw invalid("Cần công bố bài học và chủ đề trước.");
    }

    switch (kind) {
      case TOPICS -> {
        String code = text(data, "code", 50, true);
        if (!code.matches("[A-Z][A-Z0-9_]*")) throw invalid("Mã chủ đề cần dùng chữ hoa, số hoặc dấu gạch dưới.");
        text(data, "name", 150, true);
        text(data, "description", 4000, false);
      }
      case LESSONS -> {
        String level = choice(data, "level", "A2", "B1");
        var topic = db.find("SELECT is_active FROM topics WHERE id=?" + (publish ? " FOR SHARE" : ""), data.get("topic_id"))
            .orElseThrow(() -> invalid("Chủ đề không tồn tại."));
        if (publish && !topic.flag("is_active")) throw invalid("Chủ đề chưa công khai.");
        Object scenario = data.get("scenario_id");
        if (scenario != null && db.count("""
            SELECT COUNT(*) FROM scenario_levels sl JOIN scenarios s ON s.id=sl.scenario_id
            JOIN topics t ON t.id=s.topic_id WHERE sl.scenario_id=? AND sl.level=?
            """ + (publish ? " AND s.status='ACTIVE' AND t.is_active=TRUE" : ""), scenario, level) == 0)
          throw invalid("Tình huống gợi ý phải hỗ trợ cấp độ của bài và công khai khi phát hành.");
        text(data, "title", 200, true);
        text(data, "summary", 4000, true);
        String code = text(data, "objective_code", 64, true);
        if (!code.matches("[A-Z][A-Z0-9_]*")) throw invalid("Mã mục tiêu không hợp lệ.");
        text(data, "learning_objective", 4000, true);
      }
      case WORDS -> {
        String type = choice(data, "kind", "WORD", "PHRASE");
        if (data.get("part_of_speech") != null)
          choice(data, "part_of_speech", vn.englishconversation.lesson.dto.PartOfSpeech.codes());
        text(data, "expression", 200, true);
        String ipa = text(data, "ipa", 240, publish && type.equals("WORD"));
        if (ipa != null && (!ipa.matches("/[\\p{L}\\p{M} .ˈˌːˑ‿͡-]+/") || ipa.codePoints().noneMatch(Character::isLetter)))
          throw invalid("IPA phải được bao bằng dấu /, ví dụ /həˈloʊ/.");
        text(data, "meaning_vi", 4000, true);
        String example = text(data, "example_en", 2000, publish);
        String translation = text(data, "example_vi", 2000, publish);
        if ((example == null) != (translation == null)) throw invalid("Ví dụ và bản dịch phải đi cùng nhau.");
      }
      case DIALOGUES -> {
        text(data, "title", 200, true);
        try {
          SampleDialogueFormat.validate(json.readValue(text(data, "lines_json", 48000, true), new TypeReference<List<Line>>() {}));
        } catch (RuntimeException failure) {
          throw invalid("Hội thoại cần 4–6 câu đúng thứ tự, hai vai, lời Anh/Việt và mã giọng hợp lệ.");
        }
      }
      case LISTENING -> {
        text(data, "translation_vi", 4000, false);
        try {
          String segments = text(data, "script_segments_json", 48000, false);
          ListeningRules.validate(text(data, "title", 200, true), text(data, "instructions", 1000, true),
              text(data, "tts_voice_key", 80, true), text(data, "transcript_en", 4000, true),
              segments == null ? null : json.readValue(segments, new TypeReference<List<Segment>>() {}),
              json.readValue(text(data, "questions_json", 48000, true), new TypeReference<List<Question>>() {}));
        } catch (RuntimeException failure) {
          throw invalid("Đề nghe cần kịch bản, mã giọng, câu hỏi và đáp án hợp lệ.");
        }
      }
      case PRONUNCIATION -> {
        text(data, "reference_text", 1000, true);
        choice(data, "locale", "en-US");
        choice(data, "mode", "READ_ALOUD", "SHADOWING");
      }
    }
  }

  private Map<String, Object> view(MaterialKind kind, Row item) {
    var result = new LinkedHashMap<String, Object>();
    result.put("id", item.text("id"));
    result.put("status", status(kind, item));
    for (String field : kind.fields) {
      Object value = item.data().get(field);
      if (value != null && field.endsWith("_id")) value = item.text(field);
      if (value != null && field.endsWith("_json")) value = json.readTree(item.text(field));
      result.put(field, value);
    }
    return result;
  }

  private Row row(MaterialKind kind, long id, boolean lock) {
    return db.find("SELECT * FROM " + kind.table + " WHERE id=?" + (lock ? " FOR UPDATE" : ""), id)
        .orElseThrow(() -> new ApiException(404, "MATERIAL_NOT_FOUND", "Không tìm thấy học liệu."));
  }

  private static String status(MaterialKind kind, Row row) {
    if (kind != MaterialKind.TOPICS) return row.text("status");
    if (row.flag("is_active")) return "ACTIVE";
    return row.flag("admin_draft") ? "DRAFT" : "ARCHIVED";
  }

  private static void requireDraft(MaterialKind kind, Row row) {
    if (!status(kind, row).equals("DRAFT"))
      throw conflict("Chỉ sửa hoặc xóa bản nháp. Có thể tạo bản nháp mới từ nội dung này.");
  }

  private static String text(Map<String, Object> data, String field, int max, boolean required) {
    Object value = data.get(field);
    if (value == null && !required) return null;
    if (!(value instanceof String text) || text.isBlank() || text.length() > max
        || text.codePoints().anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t'))
      throw invalid("Trường " + field + " bắt buộc hợp lệ và không quá " + max + " ký tự.");
    return text;
  }

  private static String choice(Map<String, Object> data, String field, String... choices) {
    String value = text(data, field, 64, true);
    if (!List.of(choices).contains(value)) throw invalid("Giá trị không hợp lệ cho " + field + ".");
    return value;
  }

  private static long number(String field, JsonNode value) {
    if (value.isIntegralNumber() || value.isString()) {
      try {
        long parsed = Long.parseLong(value.asString());
        if (parsed >= 0) return parsed;
      } catch (NumberFormatException invalid) {
        // Report a field error for values outside BIGINT.
      }
    }
    throw invalid("Cần số nguyên hợp lệ cho " + field + ".");
  }

  private static ApiException invalid(String message) { return new ApiException(422, "MATERIAL_INVALID", message); }
  private static ApiException conflict(String message) { return new ApiException(409, "MATERIAL_CONFLICT", message); }
}
