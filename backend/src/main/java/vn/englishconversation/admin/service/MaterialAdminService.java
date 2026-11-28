package vn.englishconversation.admin.service;

import java.util.*;
import vn.englishconversation.config.features.FeatureEnabled;
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
import vn.englishconversation.admin.repository.MaterialAdminRepository;
import vn.englishconversation.admin.repository.MaterialAdminRepository.*;
import vn.englishconversation.lesson.dto.LessonMaterials.Line;
import vn.englishconversation.lesson.service.SampleDialogueFormat;
import vn.englishconversation.listening.dto.ListeningData.Question;
import vn.englishconversation.listening.dto.ListeningData.Segment;
import vn.englishconversation.listening.service.ListeningRules;

@Service
@FeatureEnabled(FeatureEnabled.Feature.ADMIN)
public class MaterialAdminService {
  private final MaterialAdminRepository store;
  private final AccountService accounts;
  private final ObjectMapper json;
  private final AdminAudit audit;

  public MaterialAdminService(MaterialAdminRepository store, AccountService accounts, ObjectMapper json, AdminAudit audit) {
    this.store = store;
    this.accounts = accounts;
    this.json = json;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public LessonCatalogOptions lessonCatalogOptions() { return store.lessonCatalogOptions(); }

  public AdminPage<Map<String, Object>> list(MaterialKind kind, int page, int size) {
    return list(kind, page, size, null);
  }

  public AdminPage<Map<String, Object>> list(MaterialKind kind, int page, int size, String expressionKind) {
    if (page < 0 || size < 1 || size > 50) throw invalid("Trang hoặc kích thước trang không hợp lệ.");
    if (expressionKind != null) {
      if (kind != MaterialKind.WORDS || !Set.of("WORD", "PHRASE").contains(expressionKind))
        throw invalid("Loại từ hoặc mẫu câu không hợp lệ.");
    }
    return new AdminPage<>(store.list(kind,page,size,expressionKind).stream().map(row -> view(kind,row)).toList(),
        page,size,store.count(kind,expressionKind));
  }

  @Transactional
  public Map<String, Object> save(AccountPrincipal actor, MaterialKind kind, Long id, Map<String, JsonNode> input) {
    accounts.requireAdminForWrite(actor);
    MaterialState previous = id == null ? null : row(kind, id, true);
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
        && !((TopicContent)previous.content()).code().equals(fields.get("code")))
      throw conflict("Mã chủ đề đã phát hành cần được giữ để bảo toàn các tham chiếu.");
    boolean creating = id == null;
    try {
      id = store.save(id,content(kind,fields));
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
      validate(kind, fields(item.content()), true);
    } else if (!status(kind, item).equals("ACTIVE")) {
      throw conflict("Chỉ ẩn nội dung đang công khai.");
    }
    if (kind == MaterialKind.TOPICS) {
      store.change(kind,id,publish);
      audit.catalog(actor.id(), "TOPIC", id, false);
    } else {
      store.change(kind,id,publish);
    }
  }

  @Transactional
  public void deleteDraft(AccountPrincipal actor, MaterialKind kind, long id) {
    accounts.requireAdminForWrite(actor);
    requireDraft(kind, row(kind, id, true));
    if (store.referenced(kind,id)) throw conflict("Bản nháp có dữ liệu tham chiếu.");
    try {
      store.delete(kind,id);
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
      var lesson = store.lesson((Long)data.get("lesson_id"),publish).orElseThrow(() -> invalid("Bài học không tồn tại."));
      if (publish && (!lesson.activeTopic() || !"ACTIVE".equals(lesson.status())))
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
        var topic = store.topic((Long)data.get("topic_id"),publish)
            .orElseThrow(() -> invalid("Chủ đề không tồn tại."));
        if (publish && !topic) throw invalid("Chủ đề chưa công khai.");
        Object scenario = data.get("scenario_id");
        if (scenario != null && !store.scenarioSupports((Long)scenario,level,publish))
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

  private Map<String, Object> view(MaterialKind kind, MaterialState item) {
    var result = new LinkedHashMap<String, Object>();
    result.put("id", Long.toString(item.id()));
    result.put("status", status(kind, item));
    var data = fields(item.content());
    for (String field : kind.fields) {
      Object value = data.get(field);
      if (value != null && field.endsWith("_id")) value = value.toString();
      if (value != null && field.endsWith("_json")) value = json.readTree(value.toString());
      result.put(field, value);
    }
    return result;
  }

  private MaterialState row(MaterialKind kind,long id,boolean lock) {
    return store.find(kind,id,lock).orElseThrow(() -> new ApiException(404,"MATERIAL_NOT_FOUND","Không tìm thấy học liệu."));
  }
  private static String status(MaterialKind kind,MaterialState row) { return row.status(); }

  private static void requireDraft(MaterialKind kind, MaterialState row) {
    if (!status(kind, row).equals("DRAFT"))
      throw conflict("Chỉ sửa hoặc xóa bản nháp. Có thể tạo bản nháp mới từ nội dung này.");
  }

  private static Content content(MaterialKind kind,Map<String,Object> fields) { return switch(kind) {
    case TOPICS -> new TopicContent((String)fields.get("code"),(String)fields.get("name"),(String)fields.get("description"),(Long)fields.get("sort_order"));
    case LESSONS -> new LessonContent((Long)fields.get("topic_id"),(String)fields.get("level"),(Long)fields.get("scenario_id"),(String)fields.get("title"),(String)fields.get("summary"),(String)fields.get("objective_code"),(String)fields.get("learning_objective"));
    case WORDS -> new WordContent((Long)fields.get("lesson_id"),(String)fields.get("kind"),(String)fields.get("expression"),(String)fields.get("ipa"),(String)fields.get("meaning_vi"),(String)fields.get("example_en"),(String)fields.get("example_vi"),(String)fields.get("part_of_speech"),(Long)fields.get("sort_order"));
    case DIALOGUES -> new DialogueContent((Long)fields.get("lesson_id"),(String)fields.get("title"),(String)fields.get("lines_json"),(Long)fields.get("sort_order"));
    case LISTENING -> new ListeningContent((Long)fields.get("lesson_id"),(String)fields.get("title"),(String)fields.get("instructions"),(String)fields.get("tts_voice_key"),(String)fields.get("transcript_en"),(String)fields.get("translation_vi"),(String)fields.get("script_segments_json"),(String)fields.get("questions_json"),(Long)fields.get("sort_order"));
    case PRONUNCIATION -> new PronunciationContent((Long)fields.get("lesson_id"),(String)fields.get("reference_text"),(String)fields.get("locale"),(String)fields.get("mode"),(Long)fields.get("sort_order"));
  }; }
  private static Map<String,Object> fields(Content content) {
    var fields=new LinkedHashMap<String,Object>();
    switch(content) {
      case TopicContent value -> {
        fields.put("code",value.code());
        fields.put("name",value.name());
        fields.put("description",value.description());
        fields.put("sort_order",value.sortOrder());
      }
      case LessonContent value -> {
        fields.put("topic_id",value.topicId());
        fields.put("level",value.level());
        fields.put("scenario_id",value.scenarioId());
        fields.put("title",value.title());
        fields.put("summary",value.summary());
        fields.put("objective_code",value.objectiveCode());
        fields.put("learning_objective",value.learningObjective());
      }
      case WordContent value -> {
        fields.put("lesson_id",value.lessonId());
        fields.put("kind",value.kind());
        fields.put("expression",value.expression());
        fields.put("ipa",value.ipa());
        fields.put("meaning_vi",value.meaningVi());
        fields.put("example_en",value.exampleEn());
        fields.put("example_vi",value.exampleVi());
        fields.put("part_of_speech",value.partOfSpeech());
        fields.put("sort_order",value.sortOrder());
      }
      case DialogueContent value -> {
        fields.put("lesson_id",value.lessonId());
        fields.put("title",value.title());
        fields.put("lines_json",value.linesJson());
        fields.put("sort_order",value.sortOrder());
      }
      case ListeningContent value -> {
        fields.put("lesson_id",value.lessonId());
        fields.put("title",value.title());
        fields.put("instructions",value.instructions());
        fields.put("tts_voice_key",value.ttsVoiceKey());
        fields.put("transcript_en",value.transcriptEn());
        fields.put("translation_vi",value.translationVi());
        fields.put("script_segments_json",value.scriptSegmentsJson());
        fields.put("questions_json",value.questionsJson());
        fields.put("sort_order",value.sortOrder());
      }
      case PronunciationContent value -> {
        fields.put("lesson_id",value.lessonId());
        fields.put("reference_text",value.referenceText());
        fields.put("locale",value.locale());
        fields.put("mode",value.mode());
        fields.put("sort_order",value.sortOrder());
      }
    }
    return fields;
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
