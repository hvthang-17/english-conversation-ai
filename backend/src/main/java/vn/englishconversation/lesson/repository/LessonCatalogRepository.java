package vn.englishconversation.lesson.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.englishconversation.lesson.dto.LessonResponse;
import vn.englishconversation.lesson.dto.LessonResponse.BookmarkKind;
import vn.englishconversation.lesson.dto.LessonResponse.Page;

@Repository
@Profile("!bootstrap & !g1")
public class LessonCatalogRepository {
  private static final String VISIBLE = "l.status='ACTIVE' AND t.is_active=TRUE";
  private static final String BASE = """
      FROM lessons l JOIN topics t ON t.id=l.topic_id
      LEFT JOIN scenarios s ON s.id=l.scenario_id AND s.status='ACTIVE'
        AND EXISTS (SELECT 1 FROM topics st WHERE st.id=s.topic_id AND st.is_active=TRUE)
      """;
  private static final String FIELDS = """
      l.id,l.title,l.summary,l.topic_id,t.name AS topic_name,l.level,l.objective_code,l.learning_objective,
      s.id AS scenario_id,s.title AS scenario_title,
      EXISTS (SELECT 1 FROM lesson_bookmarks f WHERE f.lesson_id=l.id AND f.user_id=:owner AND f.kind='FAVORITE') AS favorite,
      EXISTS (SELECT 1 FROM lesson_bookmarks w WHERE w.lesson_id=l.id AND w.user_id=:owner AND w.kind='SAVE_FOR_LATER') AS saved
      """;
  private final NamedParameterJdbcTemplate jdbc;

  public LessonCatalogRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Page catalog(
      long owner, String keyword, String level, Long topicId, String objective, Long scenarioId,
      int page, int size) {
    var params = new MapSqlParameterSource()
        .addValue("owner", owner)
        .addValue("level", level)
        .addValue("topicId", topicId)
        .addValue("objective", objective)
        .addValue("scenarioId", scenarioId);
    String filter = " WHERE " + VISIBLE;
    if (keyword != null) {
      params.addValue("term", "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
      filter += """
           AND (REPLACE(LOWER(l.title),'đ','d') COLLATE utf8mb4_0900_ai_ci LIKE :term ESCAPE '!'
             OR REPLACE(LOWER(l.summary),'đ','d') COLLATE utf8mb4_0900_ai_ci LIKE :term ESCAPE '!')
          """;
    }
    if (level != null) filter += " AND l.level=:level";
    if (topicId != null) filter += " AND l.topic_id=:topicId";
    if (objective != null) filter += " AND l.objective_code=:objective";
    if (scenarioId != null) filter += " AND s.id=:scenarioId";
    return readPage(BASE + filter, "t.sort_order,t.id,l.id", params, page, size);
  }

  public Optional<LessonResponse> detail(long owner, long id) {
    return jdbc.query(
        "SELECT " + FIELDS + ",TRUE AS available " + BASE + " WHERE " + VISIBLE + " AND l.id=:id",
        Map.of("owner", owner, "id", id), this::map)
        .stream().findFirst();
  }

  public Page saved(long owner, BookmarkKind kind, int page, int size) {
    var params = new MapSqlParameterSource("owner", owner).addValue("kind", kind.name());
    return readPage(BASE + """
        JOIN lesson_bookmarks b ON b.lesson_id=l.id AND b.user_id=:owner AND b.kind=:kind
        """, "b.created_at DESC,l.id DESC", params, page, size);
  }

  private Page readPage(String query, String order, MapSqlParameterSource params, int page, int size) {
    long total = jdbc.queryForObject("SELECT COUNT(*) " + query, params, Long.class);
    var items = jdbc.query(
        "SELECT " + FIELDS + "," + VISIBLE + " AS available " + query
            + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",
        params.addValue("limit", size).addValue("offset", (long) page * size), this::map);
    return new Page(items, page, size, total);
  }

  private LessonResponse map(ResultSet row, int index) throws SQLException {
    String id = row.getString("id");
    boolean favorite = row.getBoolean("favorite");
    boolean saved = row.getBoolean("saved");
    if (!row.getBoolean("available")) {
      return new LessonResponse(id, false, null, null, null, null, null, null, null, null, null,
          favorite, saved);
    }
    return new LessonResponse(id, true, row.getString("title"), row.getString("summary"),
        row.getString("topic_id"), row.getString("topic_name"), row.getString("level"),
        row.getString("objective_code"), row.getString("learning_objective"),
        row.getString("scenario_id"), row.getString("scenario_title"), favorite, saved);
  }

  public boolean lockVisible(long lesson) {
    return !jdbc.queryForList(
        "SELECT l.id FROM lessons l JOIN topics t ON t.id=l.topic_id WHERE "
            + VISIBLE + " AND l.id=:lesson FOR SHARE",
        Map.of("lesson", lesson), Long.class).isEmpty();
  }

  public void save(long owner, long lesson, BookmarkKind kind) {
    jdbc.update("""
        INSERT INTO lesson_bookmarks (user_id,lesson_id,kind,created_at)
        VALUES (:owner,:lesson,:kind,UTC_TIMESTAMP(6))
        ON DUPLICATE KEY UPDATE created_at=lesson_bookmarks.created_at
        """, bookmark(owner, lesson, kind));
  }

  public void remove(long owner, long lesson, BookmarkKind kind) {
    jdbc.update("DELETE FROM lesson_bookmarks WHERE user_id=:owner AND lesson_id=:lesson AND kind=:kind",
        bookmark(owner, lesson, kind));
  }

  public void removeAll(long owner) {
    jdbc.update("DELETE FROM lesson_bookmarks WHERE user_id=:owner", Map.of("owner", owner));
  }

  private static Map<String, Object> bookmark(long owner, long lesson, BookmarkKind kind) {
    return Map.of("owner", owner, "lesson", lesson, "kind", kind.name());
  }
}
