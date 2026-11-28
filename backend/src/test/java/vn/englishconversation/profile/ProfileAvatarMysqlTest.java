package vn.englishconversation.profile;


import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.englishconversation.conversation.dto.*;

import vn.englishconversation.testing.FullSystemMysqlFixture;

@EnabledIfEnvironmentVariable(named="PHASE1_MYSQL_TEST", matches="true")
class ProfileAvatarMysqlTest extends FullSystemMysqlFixture {

  @Test
  void avatarUrlIsPersistedAndOnlyServesTheOwnersCurrentFile() throws Exception {
    var json = new tools.jackson.databind.ObjectMapper();
    var base = "http://127.0.0.1:" + port + "/api/v1/";
    var browser = java.net.http.HttpClient.newBuilder()
        .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    var account = accounts.register("avatar-" + java.util.UUID.randomUUID() + "@example.test",
        "Synthetic avatar test password");
    assertEquals(200, post(browser, json, base, "auth/login",
        java.util.Map.of("email", account.email(), "password", "Synthetic avatar test password"), true).statusCode());
    byte[] png;
    try (var bytes = new java.io.ByteArrayOutputStream()) {
      javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(10, 10, 2), "png", bytes);
      png = bytes.toByteArray();
    }
    String first = uploadAvatar(browser, json, base, png);
    String path = first.substring("/api/v1/".length());
    String key = first.substring(first.lastIndexOf('/') + 1);
    var firstFile = AVATAR_TEST_DIRECTORY.resolve(key + ".png");
    assertEquals(first, jdbc.queryForObject("SELECT avatar_url FROM learner_profiles WHERE user_id=?",
        String.class, account.id()));
    var profile = request(browser, json, base, "profile/me", "GET", null, false);
    assertEquals(first, json.readTree(profile.body()).path("avatarUrl").asString());
    var imageRequest = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + path)).GET().build();
    var image = browser.send(imageRequest, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(200, image.statusCode());
    assertEquals("image/png", image.headers().firstValue("content-type").orElseThrow());
    assertArrayEquals(java.nio.file.Files.readAllBytes(firstFile), image.body());
    assertEquals(401, java.net.http.HttpClient.newHttpClient()
        .send(imageRequest, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
    assertEquals(404, request(browser, json, base, "profile/me/avatar/" + java.util.UUID.randomUUID(),
        "GET", null, false).statusCode());

    String second = uploadAvatar(browser, json, base, png);
    assertNotEquals(first, second);
    assertFalse(java.nio.file.Files.exists(firstFile));
    assertEquals(404, request(browser, json, base, path, "GET", null, false).statusCode());
    assertEquals(204, request(browser, json, base, "profile/me/avatar", "DELETE", null, true).statusCode());
    assertNull(jdbc.queryForObject("SELECT avatar_url FROM learner_profiles WHERE user_id=?", String.class, account.id()));
    assertEquals(404, request(browser, json, base, second.substring("/api/v1/".length()),
        "GET", null, false).statusCode());
  }

  private String uploadAvatar(java.net.http.HttpClient browser, tools.jackson.databind.ObjectMapper json,
      String base, byte[] png) throws Exception {
    var csrf = json.readTree(request(browser, json, base, "auth/csrf", "GET", null, false).body());
    var boundary = "avatar-test";
    var body = java.net.http.HttpRequest.BodyPublishers.concat(
        java.net.http.HttpRequest.BodyPublishers.ofString("--" + boundary
            + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"avatar.png\"\r\n"
            + "Content-Type: image/png\r\n\r\n"),
        java.net.http.HttpRequest.BodyPublishers.ofByteArray(png),
        java.net.http.HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n"));
    var response = browser.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "profile/me/avatar"))
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .header(csrf.path("headerName").asString(), csrf.path("token").asString())
        .PUT(body).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).path("avatarUrl").asString();
  }
}
