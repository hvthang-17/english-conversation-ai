package vn.englishconversation.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AdmissionProcessesMysqlTest {
  @TempDir Path directory;

  @Test
  @EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
  void twoJvmsSerializeReservationsWithoutLostUpdates() throws Exception {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:3307/phase2_auth_[0-9]{14}\\?connectionTimeZone=UTC"));
    var db = new JdbcTemplate(new DriverManagerDataSource(url,
        System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD")));
    db.execute("CREATE TABLE br_admission_probe(id INT PRIMARY KEY,reservations INT NOT NULL)");
    db.update("INSERT INTO br_admission_probe VALUES (1,0)");
    Process first = null, second = null;
    try {
      first = start("one");
      second = start("two");
      long deadline = System.nanoTime() + 20_000_000_000L;
      while (!(Files.exists(directory.resolve("one.ready")) && Files.exists(directory.resolve("two.ready")))) {
        assertTrue(first.isAlive() && second.isAlive(), "Worker exited before readiness");
        assertTrue(System.nanoTime() < deadline, "Workers did not become ready");
        Thread.sleep(20);
      }
      Files.writeString(directory.resolve("go"), "start");
      assertTrue(first.waitFor(30, TimeUnit.SECONDS));
      assertTrue(second.waitFor(30, TimeUnit.SECONDS));
      assertEquals(0, first.exitValue(), Files.readString(directory.resolve("one.log")));
      assertEquals(0, second.exitValue(), Files.readString(directory.resolve("two.log")));
      assertEquals(24, db.queryForObject("SELECT reservations FROM br_admission_probe WHERE id=1", Integer.class));
      for (String worker : new String[]{"one", "two"}) {
        String result = Files.readString(directory.resolve(worker + ".log"));
        System.out.println("admission worker=" + worker + " " + result);
        assertTrue(result.contains("samples=12 p50_ms="));
      }
    } finally {
      if (first != null && first.isAlive()) { first.destroyForcibly(); first.waitFor(10, TimeUnit.SECONDS); }
      if (second != null && second.isAlive()) { second.destroyForcibly(); second.waitFor(10, TimeUnit.SECONDS); }
      db.execute("DROP TABLE br_admission_probe");
    }
  }

  private Process start(String name) throws Exception {
    String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    return new ProcessBuilder(java, "-cp", classpath, AdmissionProcess.class.getName(),
        directory.toString(), name).redirectErrorStream(true)
        .redirectOutput(directory.resolve(name + ".log").toFile()).start();
  }
}
