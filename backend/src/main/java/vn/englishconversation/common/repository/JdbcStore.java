package vn.englishconversation.common.repository;

import java.util.*;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class JdbcStore {
  private final JdbcTemplate jdbc;

  public JdbcStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Row(Map<String, Object> data) {
    public String text(String field) {
      return Objects.toString(data.get(field), null);
    }

    public long number(String field) {
      return ((Number) data.get(field)).longValue();
    }

    public boolean flag(String field) {
      return data.get(field) instanceof Boolean b ? b : number(field) != 0;
    }
  }

  public List<Row> rows(String sql, Object... args) {
    return jdbc.queryForList(sql, args).stream().map(Row::new).toList();
  }

  public Optional<Row> find(String sql, Object... args) {
    return rows(sql, args).stream().findFirst();
  }

  public long count(String sql, Object... args) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
  }

  public void update(String sql, Object... args) {
    jdbc.update(sql, args);
  }

  public long insert(String sql, Object... args) {
    var holder = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          var statement = connection.prepareStatement(sql, new String[] {"id"});
          for (int index = 0; index < args.length; index++)
            statement.setObject(index + 1, args[index]);
          return statement;
        },
        holder);
    return Objects.requireNonNull(holder.getKey()).longValue();
  }
}
