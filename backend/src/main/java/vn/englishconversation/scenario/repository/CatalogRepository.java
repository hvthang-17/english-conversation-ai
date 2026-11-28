package vn.englishconversation.scenario.repository;

import java.util.*;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import vn.englishconversation.scenario.dto.*;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class CatalogRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public CatalogRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String ACTIVE =
      " FROM scenarios s JOIN topics t ON t.id=s.topic_id WHERE s.status='ACTIVE' AND"
          + " t.is_active=TRUE AND EXISTS (SELECT 1 FROM scenario_levels l WHERE"
          + " l.scenario_id=s.id)";

  private String filters(String level, Long topicId) {
    return (level == null
            ? ""
            : " AND EXISTS (SELECT 1 FROM scenario_levels l WHERE l.scenario_id=s.id AND"
                + " l.level=:level)")
        + (topicId == null ? "" : " AND t.id=:topicId");
  }

  public List<TopicResponse> topics(String level) {
    return jdbc.query(
        "SELECT t.id,t.code,t.name,t.description FROM topics t WHERE t.is_active=TRUE AND EXISTS"
            + " (SELECT 1 FROM scenarios s JOIN scenario_levels l ON l.scenario_id=s.id WHERE"
            + " s.topic_id=t.id AND s.status='ACTIVE'"
            + (level == null ? "" : " AND l.level=:level")
            + ") ORDER BY t.sort_order,t.id",
        new MapSqlParameterSource("level", level),
        (r, n) ->
            new TopicResponse(
                r.getString("id"),
                r.getString("code"),
                r.getString("name"),
                r.getString("description")));
  }

  public List<TopicResponse> activeTopics() {
    return jdbc.query(
        "SELECT id,code,name,description FROM topics WHERE is_active=TRUE ORDER BY sort_order,id",
        Map.of(),
        (row, index) -> new TopicResponse(
            row.getString("id"), row.getString("code"), row.getString("name"), row.getString("description")));
  }

  public List<String> interests(long userId) {
    return jdbc.query(
        "SELECT i.topic_id FROM user_topic_interests i JOIN topics t ON t.id=i.topic_id WHERE"
            + " i.user_id=:userId AND t.is_active=TRUE ORDER BY t.sort_order,t.id",
        Map.of("userId", userId),
        (r, n) -> r.getString(1));
  }

  public int lockActiveTopics(List<Long> ids) {
    if (ids.isEmpty()) return 0;
    return jdbc.queryForList(
            "SELECT id FROM topics WHERE is_active=TRUE AND id IN (:ids) FOR SHARE",
            Map.of("ids", ids),
            Long.class)
        .size();
  }

  public void replaceInterests(long userId, List<Long> ids) {
    jdbc.update("DELETE FROM user_topic_interests WHERE user_id=:userId", Map.of("userId", userId));
    for (long id : ids)
      jdbc.update(
          "INSERT INTO user_topic_interests(user_id,topic_id,created_at) VALUES"
              + " (:userId,:topicId,UTC_TIMESTAMP(6))",
          Map.of("userId", userId, "topicId", id));
  }

  private List<String> levels(long id) {
    return jdbc.queryForList(
        "SELECT level FROM scenario_levels WHERE scenario_id=:id ORDER BY level",
        Map.of("id", id),
        String.class);
  }

  public ScenarioPage list(String level, Long topicId, int page, int size) {
    var p =
        new MapSqlParameterSource()
            .addValue("level", level)
            .addValue("topicId", topicId)
            .addValue("limit", size)
            .addValue("offset", (long) page * size);
    String from = ACTIVE + filters(level, topicId);
    long total =
        Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*)" + from, p, Long.class));
    // One extra bounded query for all level links, not one query per card.
    var rows =
        jdbc.query(
            "SELECT s.id,s.code,s.topic_id,t.name AS topic_name,s.title,s.description"
                + from
                + " ORDER BY t.sort_order,t.id,s.id LIMIT :limit OFFSET :offset",
            p,
            (r, n) ->
                new ScenarioSummary(
                    r.getString("id"),
                    r.getString("code"),
                    r.getString("topic_id"),
                    r.getString("topic_name"),
                    r.getString("title"),
                    r.getString("description"),
                    List.of()));
    if (rows.isEmpty()) return new ScenarioPage(List.of(), page, size, total);
    Map<String, List<String>> links = new HashMap<>();
    jdbc.query(
        "SELECT scenario_id,level FROM scenario_levels WHERE scenario_id IN (:ids) ORDER BY level",
        Map.of("ids", rows.stream().map(ScenarioSummary::id).toList()),
        (org.springframework.jdbc.core.RowCallbackHandler)
            r -> links.computeIfAbsent(r.getString(1), k -> new ArrayList<>()).add(r.getString(2)));
    return new ScenarioPage(
        rows.stream()
            .map(
                r ->
                    new ScenarioSummary(
                        r.id(),
                        r.code(),
                        r.topicId(),
                        r.topicName(),
                        r.title(),
                        r.description(),
                        List.copyOf(links.get(r.id()))))
            .toList(),
        page,
        size,
        total);
  }

  public Optional<ScenarioDetail> detail(long id, String level) {
    var p = new MapSqlParameterSource("id", id).addValue("level", level);
    // Deliberately select public columns; never select ai_instruction for a learner projection.
    var rows =
        jdbc.query(
            "SELECT s.id,s.code,s.topic_id,t.name AS"
                + " topic_name,s.title,s.description,s.learner_role,s.ai_role,s.communication_goal,s.context_description"
                + ACTIVE
                + " AND s.id=:id"
                + filters(level, null),
            p,
            (r, n) ->
                new ScenarioDetail(
                    r.getString("id"),
                    r.getString("code"),
                    r.getString("topic_id"),
                    r.getString("topic_name"),
                    r.getString("title"),
                    r.getString("description"),
                    r.getString("learner_role"),
                    r.getString("ai_role"),
                    r.getString("communication_goal"),
                    r.getString("context_description"),
                    List.of(), List.of()));
    if (rows.isEmpty()) return Optional.empty();
    var r = rows.getFirst();
    List<ScenarioRole> roles = new ArrayList<>();
    roles.add(new ScenarioRole("DEFAULT", r.learnerRole(), r.aiRole(), r.communicationGoal(), r.contextDescription()));
    roles.addAll(jdbc.query("""
        SELECT code,learner_role,ai_role,communication_goal,context_description
        FROM scenario_role_variants WHERE scenario_id=:id ORDER BY code
        """, p, (item, n) -> new ScenarioRole(item.getString("code"), item.getString("learner_role"),
        item.getString("ai_role"), item.getString("communication_goal"), item.getString("context_description"))));
    return Optional.of(
        new ScenarioDetail(
            r.id(),
            r.code(),
            r.topicId(),
            r.topicName(),
            r.title(),
            r.description(),
            r.learnerRole(),
            r.aiRole(),
            r.communicationGoal(),
            r.contextDescription(),
            levels(id), List.copyOf(roles)));
  }
}
