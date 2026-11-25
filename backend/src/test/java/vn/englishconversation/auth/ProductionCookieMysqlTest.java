package vn.englishconversation.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"g1", "production"})
@EnabledIfEnvironmentVariable(named = "G1_MYSQL_TEST", matches = "true")
class ProductionCookieMysqlTest {
  @Value("${local.server.port}")
  int port;

  @DynamicPropertySource
  static void isolated(DynamicPropertyRegistry properties) {
    SessionMysqlTest.isolatedDatabase(properties);
  }

  @Test
  void productionCookieIsAlwaysSecureEvenOnLocalHttpProbe() throws Exception {
    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/csrf"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    String cookie = response.headers().firstValue("set-cookie").orElseThrow();
    assertTrue(cookie.contains("Secure"));
    assertTrue(cookie.contains("HttpOnly"));
    assertTrue(cookie.contains("SameSite=Lax"));
  }
}
