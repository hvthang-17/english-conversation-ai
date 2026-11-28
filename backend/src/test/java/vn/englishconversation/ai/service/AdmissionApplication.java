package vn.englishconversation.ai.service;

import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import java.net.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.EnglishConversationApplication;
import vn.englishconversation.ai.config.ChatSettings;
import vn.englishconversation.ai.provider.TextProvider;
import vn.englishconversation.ai.repository.AiWorkRepository;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.conversation.dto.*;
import vn.englishconversation.conversation.service.PracticeService;

/** Test-classpath-only control server; never packaged into the application JAR. */
public final class AdmissionApplication {
  static final String EVENT = "vn.englishconversation.Admission";
  private final ObjectMapper json = new ObjectMapper();
  private final Path directory;
  private final String name;
  private final HikariDataSource pool;
  private final JdbcTemplate jdbc;
  private final PracticeService practice;
  private final AiAdmissionService admission;
  private final SlowProvider provider;
  private final AiWorkRepository work;
  private final PlatformTransactionManager manager;
  private final List<AccountPrincipal> owners = new ArrayList<>();
  private final List<Long> sessions = new ArrayList<>();
  private final AtomicInteger peakActive = new AtomicInteger(), peakPending = new AtomicInteger();
  private final AtomicLong samples = new AtomicLong(), activeSum = new AtomicLong();
  private Recording recording;

  public static void main(String[] args) throws Exception {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:3307/phase2_auth_[0-9]{14}\\?connectionTimeZone=UTC"))
      throw new IllegalStateException("Isolated BR-13 schema required");
    Path directory = Path.of(args[0]);
    String name = args[1];
    var app = new SpringApplication(EnglishConversationApplication.class, ProbeConfiguration.class);
    var context = app.run(Arrays.copyOfRange(args, 2, args.length));
    var probe = new AdmissionApplication(directory, name, context.getBean(HikariDataSource.class),
        context.getBean(JdbcTemplate.class), context.getBean(PracticeService.class),
        context.getBean(AiAdmissionService.class), context.getBean(SlowProvider.class),
        context.getBean(AiWorkRepository.class), context.getBean(PlatformTransactionManager.class));
    probe.seed(context.getBean(AccountService.class));
    probe.reset();
    var sampler = Executors.newSingleThreadScheduledExecutor(r -> {
      var thread = new Thread(r, "br13-pool-sampler"); thread.setDaemon(true); return thread;
    });
    sampler.scheduleAtFixedRate(probe::sample, 0, 10, TimeUnit.MILLISECONDS);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 32);
    server.setExecutor(Executors.newFixedThreadPool(16));
    server.createContext("/", exchange -> {
      int status = 200;
      Object result;
      try {
        var query = new HashMap<String,String>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw != null) for (String pair : raw.split("&")) {
          var parts = pair.split("=", 2); query.put(parts[0], parts.length == 2 ? parts[1] : "");
        }
        result = probe.handle(exchange.getRequestURI().getPath(), query);
      } catch (ApiException failure) { status = failure.status; result = Map.of("code", failure.code); }
      catch (Exception failure) { status = 500; result = Map.of("code", failure.getClass().getSimpleName()); }
      byte[] bytes = probe.json.writeValueAsBytes(result);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      try (var output = exchange.getResponseBody()) { output.write(bytes); }
    });
    server.start();
    Files.writeString(directory.resolve(name + ".ready"), Integer.toString(server.getAddress().getPort()));
  }

  private AdmissionApplication(Path directory, String name, HikariDataSource pool, JdbcTemplate jdbc,
      PracticeService practice, AiAdmissionService admission, SlowProvider provider,
      AiWorkRepository work, PlatformTransactionManager manager) {
    this.directory=directory; this.name=name; this.pool=pool; this.jdbc=jdbc; this.practice=practice;
    this.admission=admission; this.provider=provider; this.work=work; this.manager=manager;
  }

  private void seed(AccountService accounts) {
    String scenario = jdbc.queryForObject("SELECT CAST(id AS CHAR) FROM scenarios WHERE code='CLUB_INTRO'", String.class);
    for (int i=0; i<16; i++) {
      String email = "br13-owner-" + (i < 12 ? i : 0) + "@example.test";
      var ids = jdbc.queryForList("SELECT id FROM users WHERE email=?", Long.class, email);
      long owner = ids.isEmpty() ? Long.parseLong(accounts.register(email, "BR13 synthetic password only").id()) : ids.getFirst();
      var principal = new AccountPrincipal(owner, 0);
      String key = "br13-session-" + i;
      var prior = jdbc.queryForList("SELECT id FROM practice_sessions WHERE user_id=? AND client_request_id=?", Long.class, owner, key);
      long session = prior.isEmpty() ? Long.parseLong(practice.create(principal,
          new CreatePracticeRequest(scenario, "A2", key)).id()) : prior.getFirst();
      owners.add(principal); sessions.add(session);
    }
  }

  private Object handle(String path, Map<String,String> q) throws Exception {
    int index = Integer.parseInt(q.getOrDefault("i", "0"));
    return switch (path) {
      case "/reset" -> { reset(); yield Map.of("ok", true); }
      case "/stats" -> stats();
      case "/delay" -> { provider.delay.set(Integer.parseInt(q.get("ms"))); yield Map.of("ok", true); }
      case "/send" -> {
        var result = practice.send(owners.get(index), sessions.get(index),
            new SendTextRequest("I would like to practise English.", UUID.randomUUID().toString()));
        if (!"SUCCEEDED".equals(result.operation().status())) throw new IllegalStateException("Task did not succeed");
        yield Map.of("code", "SUCCESS");
      }
      case "/claim", "/rollback", "/crash" -> claim(path, q, index);
      case "/reserve" -> admission.reserve(this::sameConnection);
      case "/pool-pressure", "/pool-exhausted" -> {
        var held = new ArrayList<java.sql.Connection>();
        try {
          for (int i=0; i<(path.equals("/pool-exhausted") ? 10 : 9); i++) held.add(pool.getConnection());
          yield admission.reserve(() -> {
            var result = sameConnection();
            if (pool.getHikariPoolMXBean().getActiveConnections() != held.size()+1)
              throw new IllegalStateException("Unexpected reservation connection budget");
            return result;
          });
        } finally { for (var connection : held) connection.close(); }
      }
      default -> throw new IllegalArgumentException("Unknown fixture command");
    };
  }

  private Map<String,Object> sameConnection() {
    boolean same = Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT IS_USED_LOCK(CONCAT('practice:',LEFT(SHA2(DATABASE(),256),48)))=CONNECTION_ID()", Boolean.class));
    if (!same) throw new IllegalStateException("Named lock must use the business transaction connection");
    return Map.of("code", "SUCCESS");
  }

  private Object claim(String path, Map<String,String> q, int index) {
    String kind = q.getOrDefault("kind", "normal");
    var settings = new MockEnvironment().withProperty("app.chat.global-rpm", kind.equals("global") ? "3" : "60")
        .withProperty("app.chat.user-rpm", kind.equals("user") ? "2" : "60")
        .withProperty("app.chat.global-concurrent", kind.equals("concurrent") ? "2" : "4")
        .withProperty("app.chat.total-attempt-limit", q.getOrDefault("cap", "0"));
    var coordinator = new AiRequestCoordinator(admission, work, provider, new ChatSettings(settings), manager);
    long owner = owners.get(index).id(), session = sessions.get(index);
    var task = coordinator.reserve(() -> {
      lock(owner, session);
      long request = coordinator.createRequest(owner, session, "HINT", UUID.randomUUID().toString(), "synthetic", "br13");
      return coordinator.claim(owner, session, request, 0,
          () -> new TextProvider.Prompt("Synthetic quota probe", List.of(), 64), "br13", () -> {
            if (!path.equals("/claim")) {
              jdbc.update("UPDATE br13_faults SET value=value+1 WHERE id=1");
              if (path.equals("/rollback")) throw new SyntheticRollback();
              try {
                Files.writeString(directory.resolve(name + ".claimed"), "uncommitted");
                Thread.sleep(30_000);
                throw new IllegalStateException("Crash was not triggered");
              } catch (Exception failure) { throw new IllegalStateException(failure); }
            }
          });
    });
    var outcome = coordinator.invoke(task, 2000);
    coordinator.complete(task, () -> { lock(owner, session); return true; },
        ignored -> coordinator.recordOutcome(task, outcome.outcome(), outcome.latency()), () -> true);
    return Map.of("code", "SUCCESS");
  }

  private void lock(long owner, long session) {
    jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, owner);
    jdbc.queryForObject("SELECT id FROM practice_sessions WHERE id=? AND user_id=? FOR UPDATE", Long.class, session, owner);
  }

  private synchronized void reset() {
    if (recording != null) recording.close();
    recording = new Recording(); recording.enable(EVENT).withThreshold(Duration.ZERO).withoutStackTrace();
    recording.setMaxSize(16L * 1024 * 1024); recording.start();
    peakActive.set(0); peakPending.set(0); samples.set(0); activeSum.set(0); provider.calls.set(0);
  }

  private void sample() {
    var bean = pool.getHikariPoolMXBean();
    int active = bean.getActiveConnections();
    peakActive.accumulateAndGet(active, Math::max);
    peakPending.accumulateAndGet(bean.getThreadsAwaitingConnection(), Math::max);
    activeSum.addAndGet(active); samples.incrementAndGet();
  }

  private synchronized Object stats() throws Exception {
    var path = directory.resolve(name + ".jfr"); recording.dump(path);
    var events = new ArrayList<Map<String,Object>>();
    for (var event : RecordingFile.readAllEvents(path)) if (event.getEventType().getName().equals(EVENT)) {
      var row = new LinkedHashMap<String,Object>();
      for (String field : List.of("monitorWaitNanos", "connectionWaitNanos", "lockWaitNanos", "lockHoldNanos", "reservationNanos"))
        row.put(field, event.getLong(field));
      row.put("outcome", event.getString("outcome")); events.add(row);
    }
    return Map.of("events", events, "poolMax", pool.getMaximumPoolSize(), "peakActive", peakActive.get(),
        "peakPending", peakPending.get(), "meanActive", (double) activeSum.get()/Math.max(1, samples.get()),
        "poolSamples", samples.get(), "activeNow", pool.getHikariPoolMXBean().getActiveConnections(),
        "pendingNow", pool.getHikariPoolMXBean().getThreadsAwaitingConnection(), "providerCalls", provider.calls.get());
  }

  @TestConfiguration(proxyBeanMethods=false)
  public static class ProbeConfiguration {
    @Bean @Primary SlowProvider slowProvider() { return new SlowProvider(); }
  }

  private static class SyntheticRollback extends RuntimeException {}

  public static class SlowProvider implements TextProvider {
    final AtomicInteger delay = new AtomicInteger(100), calls = new AtomicInteger();
    public boolean ready() { return true; }
    public String model() { return "br13-offline"; }
    public Outcome generate(Prompt prompt) {
      if (TransactionSynchronizationManager.isActualTransactionActive()) throw new AssertionError("Provider in transaction");
      calls.incrementAndGet();
      try { Thread.sleep(delay.get()); } catch (InterruptedException failure) { throw new IllegalStateException(failure); }
      return new Outcome("Hello! What would you like to order?", model(), null, 10L, 8L, false, null, false, false, 0);
    }
  }
}
