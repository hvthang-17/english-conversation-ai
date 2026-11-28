package vn.englishconversation.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
@EnabledIfEnvironmentVariable(named="BR13_ADMISSION_TEST", matches="true")
class AdmissionApplicationsMysqlTest {
  private static final String LOCK = "CONCAT('practice:',LEFT(SHA2(DATABASE(),256),48))";
  private final ObjectMapper json = new ObjectMapper();
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final Path evidence = Path.of(Optional.ofNullable(System.getenv("BR_ADMISSION_EVIDENCE"))
      .orElse("../.runtime/br13")).toAbsolutePath().normalize();
  private final List<Worker> workers = new ArrayList<>();
  private JdbcTemplate jdbc;

  @Test
  void measuresRealApplicationsAndFencesQuotaRollbackTimeoutAndCrash() throws Exception {
    String url=System.getenv("SPRING_DATASOURCE_URL");
    assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:3307/phase2_auth_[0-9]{14}\\?connectionTimeZone=UTC"));
    assertTrue(evidence.startsWith(Path.of("..", ".runtime").toAbsolutePath().normalize()));
    Files.createDirectories(evidence);
    var source = new DriverManagerDataSource(url, System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
    jdbc = new JdbcTemplate(source);
    var checks = new LinkedHashMap<String,Object>();
    try {
      var first = start("one");
      jdbc.execute("CREATE TABLE br13_faults(id INT PRIMARY KEY,value INT NOT NULL)");
      jdbc.update("INSERT INTO br13_faults VALUES (1,0)");
      var second = start("two");
      for (int instances : new int[]{1,2}) for (int concurrency : new int[]{1,4})
        baseline(workers.subList(0, instances), concurrency);

      quota("global", 8, 3, first, second, 0);
      quota("user", 4, 2, first, second, 0);
      quota("concurrent", 8, 2, first, second, 0);
      long cap = countAttempts()+3;
      quota("total", 8, 3, first, second, cap);
      checks.put("quota", "global=3,user=2,concurrent=2,total=baseline+3; no extra attempts/provider calls");

      reset(workers);
      try (var holder = source.getConnection(); var acquire = holder.createStatement()) {
        try (var result=acquire.executeQuery("SELECT GET_LOCK("+LOCK+",0)")) { assertTrue(result.next()); assertEquals(1,result.getInt(1)); }
        for (var worker : workers) assertEquals("ADMISSION_BUSY", call(worker, "/reserve").code());
        try (var result=acquire.executeQuery("SELECT RELEASE_LOCK("+LOCK+")")) { assertTrue(result.next()); assertEquals(1,result.getInt(1)); }
      }
      for (var worker : workers) assertEquals("SUCCESS", call(worker,"/reserve").code());
      saveStats("lock-timeout", workers);
      checks.put("getLockTimeout", "both return ADMISSION_BUSY then recover; no claim starts");
      assertLockFree();

      age(); reset(workers);
      long before=countAttempts(), requests=countRequests();
      var rollback = call(first,"/rollback?i=9");
      assertEquals(500, rollback.status()); assertEquals("SyntheticRollback", rollback.code());
      checks.put("rollbackCode", rollback.code());
      assertEquals(before,countAttempts()); assertEquals(requests,countRequests()); assertEquals(0,marker());
      assertLockFree();
      assertEquals("SUCCESS",call(second,"/claim?i=9").code());
      saveStats("rollback", workers);
      checks.put("rollback", "request/attempt/marker rolled back; surviving instance admits");

      reset(workers);
      var pressureResult = call(first,"/pool-pressure");
      assertEquals(200,pressureResult.status()); assertEquals("SUCCESS",pressureResult.code());
      checks.put("poolPressureCode",pressureResult.code());
      assertLockFree();
      assertEquals("SUCCESS",call(second,"/reserve").code());
      var pressure=saveStats("pool-pressure",workers);
      assertEquals(0,pressure.getFirst().get("peakPending").asInt());
      assertEquals(0,pressure.getFirst().get("pendingNow").asInt());
      assertEquals(0,pressure.getFirst().get("activeNow").asInt());
      checks.put("poolPressure", "9 held connections + one transaction/advisory connection: SQL commits, pending=0, lock released");

      reset(workers);
      var exhausted = call(first,"/pool-exhausted");
      assertEquals(503,exhausted.status()); assertEquals("ADMISSION_UNAVAILABLE",exhausted.code());
      assertLockFree(); assertEquals("SUCCESS",call(first,"/reserve").code());
      saveStats("pool-exhausted",workers);
      checks.put("poolExhaustedCode",exhausted.code());

      age(); reset(workers);
      before=countAttempts(); requests=countRequests();
      var crash=CompletableFuture.supplyAsync(() -> { try { return call(first,"/crash?i=11"); } catch(Exception failure) { return null; } });
      await(() -> Files.exists(evidence.resolve("one.claimed")), Duration.ofSeconds(10));
      assertNotNull(jdbc.queryForObject("SELECT IS_USED_LOCK("+LOCK+")",Long.class));
      assertEquals(before,countAttempts()); assertEquals(requests,countRequests()); assertEquals(0,marker());
      long killedAt=System.nanoTime();
      first.process().destroyForcibly(); assertTrue(first.process().waitFor(10,TimeUnit.SECONDS));
      await(() -> jdbc.queryForObject("SELECT IS_FREE_LOCK("+LOCK+")",Integer.class)==1,Duration.ofSeconds(10));
      crash.get(10,TimeUnit.SECONDS);
      assertEquals(before,countAttempts()); assertEquals(requests,countRequests()); assertEquals(0,marker());
      assertEquals("SUCCESS",call(second,"/claim?i=11").code());
      checks.put("crashRecoveryMs",(System.nanoTime()-killedAt)/1_000_000.0);
      checks.put("crash", "kill during uncommitted claim: no orphan request/attempt/marker; DB releases advisory lock; survivor completes new claim");
      saveStats("crash-survivor",List.of(second)); assertLockFree();
      Files.writeString(evidence.resolve("safety.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(checks));
    } finally {
      for (var worker : workers) if (worker.process().isAlive()) {
        worker.process().destroyForcibly(); worker.process().waitFor(10,TimeUnit.SECONDS);
      }
    }
  }

  private void baseline(List<Worker> selected,int clients) throws Exception {
    age(); batch(selected,8,1,"/send?i="); age(); reset(selected);
    long before = countAttempts(), started=System.nanoTime();
    var responses=batch(selected,48,clients,"/send?i=");
    double seconds=(System.nanoTime()-started)/1_000_000_000.0;
    assertEquals(48,responses.stream().filter(r->r.code().equals("SUCCESS")).count());
    assertEquals(48,countAttempts()-before);
    var stats=saveStats("baseline-"+selected.size()+"-"+clients,selected);
    assertEquals(48,stats.stream().mapToInt(s->s.get("providerCalls").asInt()).sum());
    var times=new ArrayList<Long>();
    for(var snapshot:stats) {
      assertEquals(0,snapshot.get("peakPending").asInt());
      for(var event:snapshot.get("events")) {
        assertEquals("SUCCESS",event.get("outcome").asString());
        times.add(event.get("reservationNanos").asLong());
      }
    }
    assertEquals(48,times.size()); Collections.sort(times);
    double p95=times.get((int)Math.ceil(times.size()*.95)-1)/1_000_000.0;
    var summary=Map.of("instances",selected.size(),"clients",clients,"samples",48,"providerDelayMs",100,
        "throughput",48/seconds,"reservationP95Ms",p95,"targetMet",p95<=500 && 48/seconds>=1);
    Files.writeString(evidence.resolve("summary-"+selected.size()+"-"+clients+".json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
    // Target misses are measurements, not grounds to suppress the remaining safety evidence.
    System.out.println("BR13 baseline "+json.writeValueAsString(summary));
  }

  private void quota(String kind,int requests,int expected,Worker first,Worker second,long cap) throws Exception {
    age(); reset(workers);
    for(var worker:workers) assertEquals(200,call(worker,"/delay?ms="+(kind.equals("concurrent")?3000:100)).status());
    long before=countAttempts();
    var results=new ArrayList<Response>();
    try(var executor=Executors.newFixedThreadPool(requests)) {
      var barrier=new CountDownLatch(1); var futures=new ArrayList<Future<Response>>();
      for(int i=0;i<requests;i++) {
        int index=kind.equals("user")?12+i:i; var worker=i%2==0?first:second;
        futures.add(executor.submit(() -> { barrier.await(); return call(worker,"/claim?kind="+kind+"&cap="+cap+"&i="+index); }));
      }
      barrier.countDown(); for(var future:futures) results.add(future.get(20,TimeUnit.SECONDS));
    }
    assertEquals(expected,results.stream().filter(r->r.code().equals("SUCCESS")).count(),kind+results);
    assertEquals(requests-expected,results.stream().filter(r->r.code().equals("AI_QUOTA") && r.status()==429).count(),kind+results);
    assertEquals(expected,countAttempts()-before);
    var snapshots=saveStats("quota-"+kind,workers);
    assertEquals(expected,snapshots.stream().mapToInt(s->s.get("providerCalls").asInt()).sum());
    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_requests WHERE status='RUNNING'",Integer.class));
    for(var worker:workers) call(worker,"/delay?ms=100"); assertLockFree();
  }

  private List<Response> batch(List<Worker> selected,int count,int concurrency,String path) throws Exception {
    try(var executor=Executors.newFixedThreadPool(concurrency)) {
      var results=new ArrayList<Future<Response>>();
      for(int i=0;i<count;i++) { int index=i; results.add(executor.submit(() -> call(selected.get(index%selected.size()),path+(index%16)))); }
      var values=new ArrayList<Response>(); for(var result:results) values.add(result.get(30,TimeUnit.SECONDS)); return values;
    }
  }

  private Worker start(String name) throws Exception {
    Files.deleteIfExists(evidence.resolve(name+".ready")); Files.deleteIfExists(evidence.resolve(name+".claimed"));
    String java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
    String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
    String url=System.getenv("SPRING_DATASOURCE_URL");
    Path storage=evidence.resolve("storage").resolve(url.substring(url.lastIndexOf('/')+1,url.indexOf('?')));
    var process=new ProcessBuilder(java,"-Xms64m","-Xmx256m","-cp",cp,AdmissionApplication.class.getName(),evidence.toString(),name,
        "--spring.profiles.active=g6","--server.address=127.0.0.1","--server.port=0","--app.features.stage=FULL",
        "--spring.datasource.hikari.maximum-pool-size=10","--spring.datasource.hikari.minimum-idle=10",
        "--spring.datasource.hikari.connection-timeout=1000","--app.chat.global-rpm=60","--app.chat.user-rpm=60",
        "--app.chat.global-concurrent=4","--app.chat.total-attempt-limit=0","--app.gemini.enabled=false",
        "--app.listening.azure.enabled=false","--app.pronunciation.azure.enabled=false",
        "--app.mail.enabled=false","--app.mail.reminders-enabled=false","--app.admin-bootstrap.enabled=false",
        "--app.voice.temp-directory="+storage.resolve("voice"),
        "--app.account.avatar-directory="+storage.resolve("avatars"),
        "--app.listening.audio-directory="+storage.resolve("listening"),
        "--app.pronunciation.audio-directory="+storage.resolve("pronunciation"),
        "--CHAT_FINGERPRINT_SECRET=br13-synthetic-fingerprint-secret-only-1234567890",
        "--app.pronunciation.fingerprint-key=br13-synthetic-pronunciation-key-only-1234567890")
        .redirectErrorStream(true).redirectOutput(evidence.resolve(name+".log").toFile()).start();
    var pending=new Worker(process,0); workers.add(pending);
    await(() -> { assertTrue(process.isAlive(),"Application exited; inspect .runtime/br13/"+name+".log"); return Files.exists(evidence.resolve(name+".ready")); },Duration.ofSeconds(90));
    var worker=new Worker(process,Integer.parseInt(Files.readString(evidence.resolve(name+".ready")).trim()));
    workers.set(workers.indexOf(pending),worker); return worker;
  }

  private Response call(Worker worker,String path) throws Exception {
    var result=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+worker.port()+path))
        .timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofString());
    var body=json.readTree(result.body());
    return new Response(result.statusCode(),body.has("code")?body.get("code").asString():"",body);
  }

  private List<JsonNode> saveStats(String label,List<Worker> selected) throws Exception {
    var results=new ArrayList<JsonNode>(); for(var worker:selected) results.add(call(worker,"/stats").body());
    Files.writeString(evidence.resolve(label+".json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(results)); return results;
  }
  private void reset(List<Worker> selected) throws Exception { for(var worker:selected) assertEquals(200,call(worker,"/reset").status()); }
  private void age() { jdbc.update("UPDATE ai_attempts SET started_at=UTC_TIMESTAMP(6)-INTERVAL 120 SECOND WHERE status<>'RUNNING'"); }
  private long countAttempts() { return jdbc.queryForObject("SELECT COUNT(*) FROM ai_attempts",Long.class); }
  private long countRequests() { return jdbc.queryForObject("SELECT COUNT(*) FROM ai_requests",Long.class); }
  private int marker() { return jdbc.queryForObject("SELECT value FROM br13_faults WHERE id=1",Integer.class); }
  private void assertLockFree() { assertEquals(1,jdbc.queryForObject("SELECT IS_FREE_LOCK("+LOCK+")",Integer.class)); }
  private void await(Callable<Boolean> condition,Duration timeout) throws Exception {
    long end=System.nanoTime()+timeout.toNanos(); while(!condition.call()) { assertTrue(System.nanoTime()<end,"Fixture deadline exceeded"); Thread.sleep(50); }
  }
  private record Worker(Process process,int port) {}
  private record Response(int status,String code,JsonNode body) {}
}
