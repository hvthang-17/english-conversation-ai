package vn.englishconversation.config;

import java.sql.SQLException;
import java.util.Arrays;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("p0-db")
class PhaseZeroDatabaseConfig {
  private static final String SCHEMA = "english_conversation";
  private static final String URL = "jdbc:mysql://127.0.0.1:3306/" + SCHEMA;

  @Bean
  FlywayMigrationStrategy phaseZeroMigrationStrategy(Environment environment) {
    return flyway -> {
      if (!environment.matchesProfiles("bootstrap")) {
        throw new IllegalStateException("Run P0 MySQL with profiles bootstrap,p0-db.");
      }
      if (environment.getProperty("P0_DB_USERNAME", "").isBlank()) {
        throw new IllegalStateException("Set P0_DB_USERNAME for the P0 database profile.");
      }
      var config = flyway.getConfiguration();
      if (!SCHEMA.equals(config.getDefaultSchema())
          || !Arrays.equals(config.getSchemas(), new String[] {SCHEMA})
          || config.getLocations().length != 1
          || !"classpath:db/migration".equals(config.getLocations()[0].getDescriptor())
          || config.isBaselineOnMigrate()
          || !config.isCleanDisabled()) {
        throw new IllegalStateException("Use the P0 Flyway schema and migration location with clean and baseline disabled.");
      }
      try (var connection = config.getDataSource().getConnection()) {
        var url = connection.getMetaData().getURL().split("\\?", 2)[0];
        if (!URL.equals(url) || !SCHEMA.equals(connection.getCatalog())) {
          throw new IllegalStateException("P0 Flyway requires " + URL + ".");
        }
        try (var statement = connection.createStatement();
            var result = statement.executeQuery("SELECT @@server_uuid, @@port, DATABASE()")) {
          if (!result.next() || result.getInt(2) != 3306 || !SCHEMA.equals(result.getString(3))) {
            throw new IllegalStateException("Unexpected P0 MySQL instance or schema.");
          }
          LoggerFactory.getLogger(PhaseZeroDatabaseConfig.class)
              .info("P0 MySQL instance UUID {}, port {}, schema {}", result.getString(1), 3306, SCHEMA);
        }
      } catch (SQLException error) {
        throw new IllegalStateException("P0 MySQL connection failed. Check P0_DB_USERNAME and P0_DB_PASSWORD.");
      }
      flyway.migrate();
    };
  }
}
