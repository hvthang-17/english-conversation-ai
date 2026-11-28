package vn.englishconversation.ai.service;

import java.nio.file.*;
import java.util.Arrays;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** Separate JVM used only by AdmissionProcessesMysqlTest. Never calls a provider. */
public class AdmissionProcess {
  public static void main(String[] args) throws Exception {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:3307/phase2_auth_[0-9]{14}\\?connectionTimeZone=UTC"))
      throw new IllegalStateException("Isolated phase2 database required");
    var ds = new DriverManagerDataSource(url, System.getenv("SPRING_DATASOURCE_USERNAME"),
        System.getenv("SPRING_DATASOURCE_PASSWORD"));
    var db = new JdbcTemplate(ds);
    var admission = new AiAdmissionService(ds, new DataSourceTransactionManager(ds));
    Path directory = Path.of(args[0]);
    Files.writeString(directory.resolve(args[1] + ".ready"), "ready");
    long deadline = System.nanoTime() + 20_000_000_000L;
    while (!Files.exists(directory.resolve("go"))) {
      if (System.nanoTime() > deadline) throw new IllegalStateException("Start timeout");
      Thread.sleep(10);
    }
    long[] durations = new long[12];
    for (int i = 0; i < durations.length; i++) {
      long start = System.nanoTime();
      admission.reserve(() -> {
        int before = db.queryForObject("SELECT reservations FROM br_admission_probe WHERE id=1", Integer.class);
        try { Thread.sleep(15); } catch (InterruptedException e) { throw new IllegalStateException(e); }
        // Deliberately no row lock: the cross-process admission lock must serialize read/write.
        db.update("UPDATE br_admission_probe SET reservations=? WHERE id=1", before + 1);
        return null;
      });
      durations[i] = (System.nanoTime() - start) / 1_000_000;
    }
    Arrays.sort(durations);
    System.out.println("samples=12 p50_ms=" + durations[5] + " p95_ms=" + durations[11]);
  }
}
