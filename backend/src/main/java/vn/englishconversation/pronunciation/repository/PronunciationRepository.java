package vn.englishconversation.pronunciation.repository;

import static vn.englishconversation.pronunciation.dto.PronunciationData.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.common.repository.JdbcStore;
import vn.englishconversation.common.repository.JdbcStore.Row;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.PRONUNCIATION)
public class PronunciationRepository {
  public static final String VERSION = "azure-en-us-ipa-short-v1";
  public static final String PROVIDER = "AZURE_SPEECH";
  private static final String CATALOG = """
      SELECT d.*, l.title AS lesson_title, l.level
      FROM pronunciation_drills d JOIN lessons l ON l.id=d.lesson_id
      JOIN topics t ON t.id=l.topic_id
      WHERE d.status='ACTIVE' AND l.status='ACTIVE' AND t.is_active=TRUE
      """;
  private final JdbcStore db;
  private final ObjectMapper json;

  public PronunciationRepository(JdbcStore db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public List<Drill> drills() {
    return db.rows(CATALOG + " ORDER BY l.id,d.sort_order,d.id").stream().map(this::drill).toList();
  }

  public Optional<Drill> activeDrill(long id) {
    return db.find(CATALOG + " AND d.id=?", id).map(this::drill);
  }

  public boolean lockActiveUser(long user) {
    return db.find("SELECT id FROM users WHERE id=? AND status='ACTIVE' AND deletion_requested_at IS NULL FOR UPDATE",
        user).isPresent();
  }

  public Optional<SubmittedAttempt> submitted(long user, String key) {
    return db.find("""
        SELECT p.*,r.request_fingerprint FROM pronunciation_attempts p JOIN ai_requests r ON r.id=p.ai_request_id
        WHERE p.user_id=? AND p.client_request_id=?
        """, user, key).map(row -> new SubmittedAttempt(row.number("id"),row.text("request_fingerprint")));
  }

  public boolean lockUser(long user) {
    return db.find("SELECT id FROM users WHERE id=? FOR UPDATE", user).isPresent();
  }

  public boolean reserveDaily(String key, int limit) {
    db.update("""
        INSERT INTO account_rate_limits(bucket_key,window_start,last_request_at,request_count)
        VALUES (?,UTC_DATE(),UTC_TIMESTAMP(6),0)
        ON DUPLICATE KEY UPDATE bucket_key=bucket_key
        """, key);
    var bucket = db.find("""
        SELECT request_count,window_start<UTC_DATE() AS previous_day
        FROM account_rate_limits WHERE bucket_key=? FOR UPDATE
        """, key).orElseThrow();
    if (!bucket.flag("previous_day") && bucket.number("request_count") >= limit) return false;
    db.update("""
        UPDATE account_rate_limits SET request_count=IF(window_start<UTC_DATE(),1,request_count+1),
          window_start=UTC_DATE(),last_request_at=UTC_TIMESTAMP(6) WHERE bucket_key=?
        """, key);
    return true;
  }

  public boolean busy(long user) {
    return db.count("SELECT COUNT(*) FROM pronunciation_attempts WHERE user_id=? AND status IN ('RUNNING','PENDING')",
        user) > 0;
  }

  public long stage(long user, Drill drill, String key, String fingerprint, String file) {
    long request = db.insert("""
        INSERT INTO ai_requests (user_id,task_type,operation_key,request_fingerprint,prompt_version,created_at)
        VALUES (?,'PRONUNCIATION',?,?,?,UTC_TIMESTAMP(6))
        """, user, key, fingerprint, VERSION);
    return db.insert("""
        INSERT INTO pronunciation_attempts
        (user_id,drill_id,ai_request_id,client_request_id,drill_mode,status,reference_text_snapshot,
         provider,locale,assessment_version,audio_storage_key,audio_expires_at,created_at)
        VALUES (?,?,?,?,?,'PENDING',?,?,?,?,?,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,UTC_TIMESTAMP(6))
        """, user, drill.id(), request, key, drill.mode(), drill.referenceText(), PROVIDER,
        drill.locale(), VERSION, file);
  }

  public Optional<Attempt> find(long user, long id) {
    return db.find("SELECT * FROM pronunciation_attempts WHERE user_id=? AND id=? AND mode='SCRIPTED'", user, id)
        .map(this::attempt);
  }

  public History history(long user, int page) {
    int size = 15;
    var rows = db.rows("SELECT * FROM pronunciation_attempts WHERE user_id=? AND mode='SCRIPTED' ORDER BY id DESC LIMIT ? OFFSET ?",
        user, size + 1, (long) page * size);
    return new History(rows.stream().limit(size).map(this::attempt).toList(), page, rows.size() > size);
  }

  public List<Candidate> candidates() {
    return db.rows("""
        SELECT p.id,p.user_id FROM pronunciation_attempts p JOIN ai_requests r ON r.id=p.ai_request_id
        WHERE p.status='PENDING' AND p.audio_expires_at>UTC_TIMESTAMP(6)+INTERVAL 50 SECOND
        AND (r.retry_not_before IS NULL OR r.retry_not_before<=UTC_TIMESTAMP(6))
        ORDER BY p.id LIMIT 10
        """).stream().map(row -> new Candidate(row.number("id"),row.number("user_id"))).toList();
  }

  public record SubmittedAttempt(long id, String requestFingerprint) {}
  public record Candidate(long id, long userId) {}

  public record Job(long id, long user, long request, int run, String audio, String reference, String locale) {}

  public Optional<Job> claim(long id, long user, BooleanSupplier reserveCall) {
    return db.find("""
        SELECT p.*,r.attempt_count FROM pronunciation_attempts p JOIN ai_requests r ON r.id=p.ai_request_id
        WHERE p.id=? AND p.user_id=? AND p.status='PENDING' AND r.status='PENDING'
        AND p.audio_expires_at>UTC_TIMESTAMP(6)+INTERVAL 50 SECOND
        AND (r.retry_not_before IS NULL OR r.retry_not_before<=UTC_TIMESTAMP(6))
        FOR UPDATE
        """, id, user).flatMap(row -> {
      long request = row.number("ai_request_id");
      if (!reserveCall.getAsBoolean()) {
        db.update("""
            UPDATE ai_requests SET status='FAILED',error_code='AZURE_QUOTA',retryable=FALSE,
              retry_not_before=NULL,finished_at=UTC_TIMESTAMP(6) WHERE id=?
            """, request);
        db.update("UPDATE pronunciation_attempts SET status='FAILED',error_code='AZURE_QUOTA' WHERE id=?", id);
        return Optional.empty();
      }
      int run = (int) row.number("attempt_count") + 1;
      db.update("""
          UPDATE ai_requests SET status='RUNNING',attempt_count=?,retryable=FALSE,retry_not_before=NULL,
            processing_expires_at=UTC_TIMESTAMP(6)+INTERVAL 90 SECOND WHERE id=?
          """, run, request);
      db.update("UPDATE pronunciation_attempts SET status='RUNNING',error_code=NULL WHERE id=?", id);
      db.update("""
          INSERT INTO ai_attempts(ai_request_id,attempt_no,provider,model_name,started_at)
          VALUES (?,?,?,'PRONUNCIATION_ASSESSMENT',UTC_TIMESTAMP(6))
          """, request, run, PROVIDER);
      return Optional.of(new Job(id, user, request, run, row.text("audio_storage_key"), row.text("reference_text_snapshot"),
          row.text("locale")));
    });
  }

  public boolean ownsClaim(Job job) {
    return db.find("""
        SELECT id FROM ai_requests WHERE id=? AND status='RUNNING' AND attempt_count=?
        AND processing_expires_at>UTC_TIMESTAMP(6) FOR UPDATE
        """, job.request(), job.run()).isPresent();
  }

  public void complete(Job job, Assessment result, String status, String error, boolean retry, long elapsed) {
    String callStatus = switch (status) {
      case "SUCCEEDED", "INSUFFICIENT_EVIDENCE" -> "SUCCEEDED";
      case "UNKNOWN" -> "UNKNOWN";
      default -> "FAILED";
    };
    db.update("""
        UPDATE ai_attempts SET status=?,error_code=?,latency_ms=?,finished_at=UTC_TIMESTAMP(6)
        WHERE ai_request_id=? AND attempt_no=? AND status='RUNNING'
        """, callStatus, error, elapsed, job.request(), job.run());
    db.update("""
        UPDATE ai_requests SET status=?,error_code=?,retryable=?,processing_expires_at=NULL,
          finished_at=IF(?,NULL,UTC_TIMESTAMP(6)),
          retry_not_before=IF(?,UTC_TIMESTAMP(6)+INTERVAL 60 SECOND,NULL) WHERE id=?
        """, retry ? "PENDING" : callStatus, error, retry, retry, retry, job.request());
    db.update("""
        UPDATE pronunciation_attempts SET status=?,error_code=?,recognized_text=?,overall_score=?,accuracy=?,
          fluency=?,completeness=?,detail_json=?,scored_at=IF(?='SUCCEEDED',UTC_TIMESTAMP(6),NULL) WHERE id=?
        """, retry ? "PENDING" : status, error,
        result == null ? null : result.recognizedText(), result == null ? null : result.overall(),
        result == null ? null : result.accuracy(), result == null ? null : result.fluency(),
        result == null ? null : result.completeness(),
        result == null ? null : json.writeValueAsString(result.words()), status, job.id());
  }

  public void expire(long id) {
    var row = db.find("""
        SELECT p.id,p.ai_request_id,p.status FROM pronunciation_attempts p JOIN ai_requests r ON r.id=p.ai_request_id
        WHERE p.id=? AND ((p.status='RUNNING' AND r.processing_expires_at<=UTC_TIMESTAMP(6))
            OR (p.status='PENDING' AND p.audio_expires_at<=UTC_TIMESTAMP(6))) FOR UPDATE
        """, id);
    if (row.isEmpty()) return;
    long request = row.get().number("ai_request_id");
    String status = "RUNNING".equals(row.get().text("status")) ? "UNKNOWN" : "FAILED";
    db.update("""
        UPDATE ai_attempts SET status='UNKNOWN',error_code='PROCESSING_EXPIRED',finished_at=UTC_TIMESTAMP(6),
        latency_ms=TIMESTAMPDIFF(MICROSECOND,started_at,UTC_TIMESTAMP(6)) DIV 1000
        WHERE ai_request_id=? AND status='RUNNING'
        """, request);
    db.update("""
        UPDATE ai_requests SET status=?,error_code='PROCESSING_EXPIRED',retryable=FALSE,finished_at=UTC_TIMESTAMP(6),
        retry_not_before=NULL,processing_expires_at=NULL WHERE id=?
        """, status, request);
    db.update("UPDATE pronunciation_attempts SET status=?,error_code='PROCESSING_EXPIRED' WHERE id=?", status, id);
  }

  public List<Candidate> cleanupCandidates() {
    return db.rows("""
        SELECT p.id,p.user_id FROM pronunciation_attempts p JOIN ai_requests r ON r.id=p.ai_request_id
        WHERE p.audio_storage_key IS NOT NULL AND
            (p.status NOT IN ('PENDING','RUNNING') OR p.audio_expires_at<=UTC_TIMESTAMP(6)
              OR r.processing_expires_at<=UTC_TIMESTAMP(6))
        ORDER BY p.id LIMIT 50
        """).stream().map(row -> new Candidate(row.number("id"),row.number("user_id"))).toList();
  }

  public Optional<String> terminalAudio(long id) {
    return db.find("""
        SELECT audio_storage_key FROM pronunciation_attempts
        WHERE id=? AND status NOT IN ('PENDING','RUNNING') AND audio_storage_key IS NOT NULL FOR UPDATE
        """, id).map(row -> row.text("audio_storage_key"));
  }

  public void audioRemoved(long id) {
    db.update("""
        UPDATE pronunciation_attempts SET audio_storage_key=NULL,audio_deleted_at=UTC_TIMESTAMP(6) WHERE id=?
        """, id);
  }

  public void pruneTechnicalAttempts() {
    db.update("""
        DELETE FROM ai_attempts WHERE provider='AZURE_SPEECH' AND model_name='PRONUNCIATION_ASSESSMENT'
        AND finished_at<UTC_TIMESTAMP(6)-INTERVAL 30 DAY LIMIT 500
        """);
  }

  public boolean hasAudio(String key) {
    return db.count("SELECT COUNT(*) FROM pronunciation_attempts WHERE audio_storage_key=?", key) > 0;
  }

  public List<String> accountAudio(long user) {
    return db.rows("SELECT audio_storage_key FROM pronunciation_attempts WHERE user_id=? AND audio_storage_key IS NOT NULL",
        user).stream().map(row -> row.text("audio_storage_key")).toList();
  }

  public void clear(long user) {
    db.update("DELETE FROM ai_requests WHERE user_id=? AND task_type='PRONUNCIATION' AND session_id IS NULL", user);
  }

  private Drill drill(Row row) {
    return new Drill(row.number("id"), row.number("lesson_id"), row.text("lesson_title"), row.text("level"),
        row.text("reference_text"), row.text("locale"), row.text("mode"));
  }

  private Attempt attempt(Row row) {
    var detail = row.text("detail_json");
    return new Attempt(row.number("id"), row.number("drill_id"), row.text("drill_mode"),
        row.text("reference_text_snapshot"), row.text("locale"), row.text("assessment_version"),
        row.text("status"), row.text("error_code"), row.text("recognized_text"),
        (BigDecimal) row.data().get("overall_score"), (BigDecimal) row.data().get("accuracy"),
        (BigDecimal) row.data().get("fluency"), (BigDecimal) row.data().get("completeness"),
        detail == null ? List.of() : Arrays.asList(json.readValue(detail, Word[].class)), row.text("created_at"));
  }
}
