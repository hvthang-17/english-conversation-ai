package vn.englishconversation.conversation.repository;

import java.util.List;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Repository;
import vn.englishconversation.common.repository.JdbcStore;

/**
 * Conversation persistence. For writes, caller locks the active owner (including security version),
 * then the owned session, in the same transaction. priorSession and ownedSession(lock=true) acquire
 * row locks and require an existing transaction. Projection-only reads such as messages may also
 * be used outside a transaction when validating provider output; they do not authorize writes.
 */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class PracticeRepository {
  private final JdbcStore db;

  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public PracticeRepository(JdbcStore db, org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.db = db;
    this.jdbc = jdbc;
  }

  public record Message(long id, long turnId, int turnNumber, String sender, String content,
                        String createdAt) {}

  public record Session(long id, long version, String mode, String roleCode, String selectedLevel,
                        String status, String snapshot, java.time.Instant startedAt, java.time.Instant endedAt) {}

  private static Session session(java.sql.ResultSet row, int index) throws java.sql.SQLException {
    var utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
    var started = row.getTimestamp("started_at", utc);
    var ended = row.getTimestamp("ended_at", utc);
    return new Session(row.getLong("id"), row.getLong("version"), row.getString("mode"),
        row.getString("role_code"), row.getString("selected_level"), row.getString("status"),
        row.getString("scenario_snapshot"), started.toInstant(), ended == null ? null : ended.toInstant());
  }

  public java.util.Optional<Session> ownedSession(long id, long owner, boolean lock) {
    return jdbc.query("SELECT * FROM practice_sessions WHERE id=? AND user_id=?" + (lock ? " FOR UPDATE" : ""),
        PracticeRepository::session, id, owner).stream().findFirst();
  }

  public Session session(long id) {
    return jdbc.query("SELECT * FROM practice_sessions WHERE id=?", PracticeRepository::session, id).stream()
        .findFirst().orElseThrow();
  }

  public boolean lockActiveAccount(long owner, long version) {
    return db.find("SELECT id FROM users WHERE id=? AND status='ACTIVE' AND deletion_requested_at IS NULL AND security_version=? FOR UPDATE",
        owner, version).isPresent();
  }

  public long opening(long session) {
    return db.find("SELECT ai_request_id FROM conversation_turns WHERE session_id=? AND turn_no=0", session)
        .orElseThrow().number("ai_request_id");
  }

  public int turnCount(long session) {
    return (int) db.count("SELECT COUNT(*) FROM conversation_turns WHERE session_id=? AND turn_no>0", session);
  }

  public boolean busy(long session, long allowed) {
    return db.count("SELECT COUNT(*) FROM ai_requests WHERE busy_session_id=? AND id<>?", session, allowed) > 0;
  }

  public boolean hasSuccessfulExchange(long session) {
    return db.find("""
        SELECT t.id FROM conversation_turns t
        JOIN messages learner ON learner.turn_id=t.id AND learner.sender='LEARNER'
        JOIN messages assistant ON assistant.turn_id=t.id AND assistant.sender='ASSISTANT'
        WHERE t.session_id=? AND t.turn_no>0 AND t.status='SUCCEEDED' LIMIT 1
        """, session).isPresent();
  }

  public java.util.Optional<String> assistantSource(long session, long message) {
    return db.find("SELECT content FROM messages WHERE session_id=? AND id=? AND sender='ASSISTANT'", session, message)
        .map(row -> row.text("content"));
  }

  public long latestAssistant(long session) {
    return db.find("""
        SELECT m.id FROM messages m JOIN conversation_turns t ON t.id=m.turn_id
        WHERE m.session_id=? AND m.sender='ASSISTANT' ORDER BY t.turn_no DESC LIMIT 1
        """, session).orElseThrow().number("id");
  }

  public List<Message> messages(long session) {
    return db.rows("""
        SELECT m.*,t.turn_no,DATE_FORMAT(m.created_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS created_iso
        FROM messages m JOIN conversation_turns t ON t.id=m.turn_id WHERE m.session_id=?
        ORDER BY t.turn_no,CASE m.sender WHEN 'LEARNER' THEN 0 ELSE 1 END
        """, session).stream().map(row -> new Message(row.number("id"), row.number("turn_id"),
            (int) row.number("turn_no"), row.text("sender"), row.text("content"),
            row.text("created_iso"))).toList();
  }

  public long addTurn(long session, int number, String key, String fingerprint, String kind, long request) {
    return db.insert("""
        INSERT INTO conversation_turns(session_id,turn_no,client_request_id,request_fingerprint,kind,status,ai_request_id,created_at)
        VALUES (?,?,?,?,?,'PENDING',?,UTC_TIMESTAMP(6))
        """, session, number, key, fingerprint, kind, request);
  }

  public void addMessage(long session, long turn, String sender, String text, String type) {
    db.insert("""
        INSERT INTO messages(session_id,turn_id,sender,content,input_type,created_at)
        VALUES (?,?,?,?,?,UTC_TIMESTAMP(6))
        """, session, turn, sender, text, type);
  }

  public void touch(long session) {
    db.update("UPDATE practice_sessions SET last_activity_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=?", session);
  }

  public record AcceptedAudio(String content,long aiRequestId) {}
  public record PriorSession(
      long id,
      long scenarioId,
      String selectedLevel,
      String initialMode,
      String roleCode
  ) {}
  public record PriorTurn(long aiRequestId,String kind,String requestFingerprint) {}
  public record Turn(long id,long turnNo,String clientRequestId,String kind,String status,long aiRequestId) {}
  public record TurnId(long id) {}
  public record TurnNumber(long turnNo) {}

  public java.util.Optional<PriorSession> priorSession(long owner,String key) {
    return db.find("SELECT * FROM practice_sessions WHERE user_id=? AND client_request_id=? FOR"
                          + " UPDATE", owner, key).map(row -> new PriorSession(row.number("id"), row.number("scenario_id"), row.text("selected_level"), row.text("initial_mode"), row.text("role_code")));
  }

  public long createSession(
      long owner,
      long scenario,
      String key,
      String fingerprint,
      String level,
      String snapshot,
      String mode,
      String initialMode,
      String role
  ) {
    return db.insert("""
INSERT INTO practice_sessions(user_id,scenario_id,client_request_id,request_fingerprint,selected_level,
scenario_snapshot,mode,initial_mode,role_code,status,started_at,last_activity_at)
VALUES (?,?,?,?,?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
""", owner, scenario, key, fingerprint, level, snapshot, mode, initialMode, role);
  }

  public void switchToText(long session) {
    db.update("UPDATE practice_sessions SET mode='TEXT',version=version+1 WHERE id=?", session);
  }

  public java.util.Optional<PriorTurn> priorTurn(long session,String key) {
    return db.find("""
                      SELECT t.ai_request_id,t.kind,t.request_fingerprint FROM conversation_turns t
                      WHERE t.session_id=? AND t.client_request_id=?
                      """, session, key).map(row -> new PriorTurn(row.number("ai_request_id"), row.text("kind"), row.text("request_fingerprint")));
  }

  public long failedTurnCount(long session,int turn) {
    return db.count("SELECT COUNT(*) FROM conversation_turns WHERE session_id=? AND turn_no=? AND status='FAILED'", session, turn);
  }

  public long addLearnerMessage(long session,long turn,String content,String inputType,Long audio) {
    return db.insert("""
        INSERT INTO messages(session_id,turn_id,sender,content,input_type,audio_submission_id,created_at)
        VALUES (?,?,'LEARNER',?,?,?,UTC_TIMESTAMP(6))
        """, session, turn, content, inputType, audio);
  }

  public java.util.Optional<AcceptedAudio> acceptedAudio(long session,long audio) {
    return db.find("""
            SELECT m.content,t.ai_request_id FROM messages m JOIN conversation_turns t ON t.id=m.turn_id
            WHERE m.session_id=? AND m.audio_submission_id=?
            """, session, audio).map(row -> new AcceptedAudio(row.text("content"), row.number("ai_request_id")));
  }

  public java.util.Optional<TurnNumber> turnForOperation(long operation) {
    return db.find("SELECT turn_no FROM conversation_turns WHERE ai_request_id=?", operation).map(row -> new TurnNumber(row.number("turn_no")));
  }

  public void skipTurn(long operation) {
    db.update("UPDATE conversation_turns SET status='SKIPPED',version=version+1 WHERE"
                  + " ai_request_id=?", operation);
  }

  public void finishSession(String status,long session) {
    db.update("""
UPDATE practice_sessions SET status=?,ended_at=UTC_TIMESTAMP(6),last_activity_at=UTC_TIMESTAMP(6),
version=version+1 WHERE id=?
""", status, session);
  }

  public void startTurn(int lease,long operation) {
    db.update("""
        UPDATE conversation_turns SET status='RUNNING',completed_at=NULL,version=version+1,
        processing_expires_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)) WHERE ai_request_id=?
        """, lease, operation);
  }

  public void completeTurn(String status,long operation) {
    db.update("""
UPDATE conversation_turns SET status=?,processing_expires_at=NULL,completed_at=UTC_TIMESTAMP(6),
version=version+1 WHERE ai_request_id=?
""", status, operation);
  }

  public java.util.Optional<TurnId> turnId(long operation) {
    return db.find("SELECT id FROM conversation_turns WHERE ai_request_id=?", operation).map(row -> new TurnId(row.number("id")));
  }

  public void expireTurn(long operation) {
    db.update("""
        UPDATE conversation_turns SET status='UNKNOWN',completed_at=UTC_TIMESTAMP(6),
        processing_expires_at=NULL,version=version+1 WHERE ai_request_id=?
        """, operation);
  }

  public java.util.List<Turn> turns(long session) {
    return db.rows("SELECT * FROM conversation_turns WHERE session_id=? ORDER BY turn_no", session).stream().map(row -> new Turn(row.number("id"), row.number("turn_no"), row.text("client_request_id"), row.text("kind"), row.text("status"), row.number("ai_request_id"))).toList();
  }
}
