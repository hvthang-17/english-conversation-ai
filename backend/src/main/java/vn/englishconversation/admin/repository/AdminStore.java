package vn.englishconversation.admin.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import vn.englishconversation.admin.dto.*;
import vn.englishconversation.admin.dto.AdminCatalog.*;
import vn.englishconversation.user.dto.UserResponse;

@Repository
@Profile("!bootstrap")
public class AdminStore {
  private final JdbcTemplate db;
  private static final String TOPIC_FIELDS = "id,code,name,description,is_active,sort_order";
  private static final String SCENARIO_FIELDS = """
      s.id,s.code,s.title,s.description,s.learner_role,s.ai_role,s.communication_goal,
      s.context_description,s.ai_instruction,s.status,
      (SELECT GROUP_CONCAT(l.level ORDER BY l.level)
        FROM scenario_levels l WHERE l.scenario_id=s.id) AS levels
      """;

  public AdminStore(JdbcTemplate db) { this.db = db; }

  public AdminPage<UserResponse> users(String email, int page, int size) {
    String match = "%" + email.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    String where = " FROM users WHERE email LIKE ? ESCAPE '!'";
    return new AdminPage<>(db.query("SELECT id,email,role,status" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
        (r, n) -> new UserResponse(r.getString("id"), r.getString("email"),
            r.getString("role"), r.getString("status")), match, size, (long) page * size),
        page, size, count("SELECT COUNT(*)" + where, match));
  }

  public AdminPage<Topic> topics(int page, int size) {
    return new AdminPage<>(db.query("SELECT " + TOPIC_FIELDS + " FROM topics ORDER BY sort_order,id LIMIT ? OFFSET ?",
        (r, n) -> topic(r), size, (long) page * size), page, size, count("SELECT COUNT(*) FROM topics"));
  }

  public Optional<Topic> topic(long id, boolean lock) {
    return db.query("SELECT " + TOPIC_FIELDS + " FROM topics WHERE id=?" + (lock ? " FOR UPDATE" : ""),
        (r, n) -> topic(r), id).stream().findFirst();
  }

  private static Topic topic(ResultSet r) throws SQLException {
    return new Topic(r.getString("id"), r.getString("code"), r.getString("name"),
        r.getString("description"), r.getBoolean("is_active"), r.getInt("sort_order"));
  }

  public long saveTopic(Long id, TopicInput input) {
    String fields = "code=?,name=?,description=?,is_active=?,sort_order=?,updated_at=UTC_TIMESTAMP(6)";
    Object[] args = {input.code(), input.name().strip(), input.description() == null ? null : input.description().strip(),
        input.active(), input.sortOrder()};
    if (id == null)
      return insert("INSERT INTO topics SET " + fields + ",created_at=UTC_TIMESTAMP(6)", args);
    db.update("UPDATE topics SET " + fields + " WHERE id=?", append(args, id));
    return id;
  }

  public AdminPage<Scenario> scenarios(long topicId, int page, int size) {
    String from = " FROM scenarios s WHERE s.topic_id=?";
    return new AdminPage<>(db.query("SELECT " + SCENARIO_FIELDS + from + " ORDER BY s.id DESC LIMIT ? OFFSET ?",
        (r, n) -> scenario(r), topicId, size, (long) page * size), page, size,
        count("SELECT COUNT(*)" + from, topicId));
  }

  public Optional<Scenario> scenario(long topicId, long id) {
    return db.query("SELECT " + SCENARIO_FIELDS + " FROM scenarios s WHERE s.topic_id=? AND s.id=?",
        (r, n) -> scenario(r), topicId, id).stream().findFirst();
  }

  private static Scenario scenario(ResultSet r) throws SQLException {
    String levels = r.getString("levels");
    return new Scenario(r.getString("id"), r.getString("code"), r.getString("title"),
        r.getString("description"), r.getString("learner_role"), r.getString("ai_role"),
        r.getString("communication_goal"), r.getString("context_description"), r.getString("ai_instruction"),
        r.getString("status"), levels == null ? List.of() : List.of(levels.split(",")));
  }

  public long saveScenario(long actor, long topicId, Long id, ScenarioInput input) {
    String fields = """
        code=?,title=?,description=?,learner_role=?,ai_role=?,communication_goal=?,
        context_description=?,ai_instruction=?,status=?,updated_by=?,updated_at=UTC_TIMESTAMP(6)
        """;
    Object[] args = {input.code(), input.title().strip(), input.description().strip(),
        input.learnerRole().strip(), input.aiRole().strip(), input.communicationGoal().strip(),
        input.contextDescription().strip(), input.aiInstruction().strip(), input.status(), actor};
    long key;
    if (id == null) {
      key = insert("INSERT INTO scenarios SET " + fields + ",topic_id=?,created_by=?,created_at=UTC_TIMESTAMP(6)",
          append(args, topicId, actor));
    } else {
      db.update("UPDATE scenarios SET " + fields + " WHERE topic_id=? AND id=?", append(args, topicId, id));
      key = id;
      db.update("DELETE FROM scenario_levels WHERE scenario_id=?", key);
    }
    for (String level : input.levels())
      db.update("INSERT INTO scenario_levels(scenario_id,level) VALUES (?,?)", key, level);
    return key;
  }

  public AdminOverview overview() {
    return db.queryForObject("""
        SELECT (SELECT COUNT(*) FROM users WHERE role='LEARNER') AS learners,
          (SELECT COUNT(*) FROM users WHERE status='LOCKED') AS locked_accounts,
          (SELECT COUNT(*) FROM topics WHERE is_active=TRUE) AS visible_topics,
          (SELECT COUNT(*) FROM scenarios s JOIN topics t ON t.id=s.topic_id
            WHERE t.is_active=TRUE AND s.status='ACTIVE'
              AND EXISTS(SELECT 1 FROM scenario_levels l WHERE l.scenario_id=s.id)) AS visible_scenarios,
          (SELECT COUNT(*) FROM practice_sessions WHERE status='COMPLETED') AS completed_practices,
          COUNT(*) AS attempts, COUNT(CASE WHEN status='SUCCEEDED' THEN 1 END) AS succeeded,
          COUNT(CASE WHEN status='FAILED' THEN 1 END) AS failed,
          COUNT(CASE WHEN status='UNKNOWN' THEN 1 END) AS unknown
        FROM ai_attempts WHERE started_at>=UTC_TIMESTAMP(6)-INTERVAL 24 HOUR
        """, (r, n) -> new AdminOverview(r.getLong("learners"), r.getLong("locked_accounts"),
            r.getLong("visible_topics"), r.getLong("visible_scenarios"), r.getLong("completed_practices"),
            r.getLong("attempts"), r.getLong("succeeded"), r.getLong("failed"), r.getLong("unknown")));
  }

  private long count(String sql, Object... args) {
    return Objects.requireNonNull(db.queryForObject(sql, Long.class, args));
  }

  private long insert(String sql, Object... args) {
    var keys = new GeneratedKeyHolder();
    db.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
      for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
      return statement;
    }, keys);
    return Objects.requireNonNull(keys.getKey()).longValue();
  }

  private static Object[] append(Object[] fields, Object... tail) {
    var result = java.util.Arrays.copyOf(fields, fields.length + tail.length);
    System.arraycopy(tail, 0, result, fields.length, tail.length);
    return result;
  }
}
