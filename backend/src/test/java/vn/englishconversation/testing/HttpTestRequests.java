package vn.englishconversation.testing;

/** Cookie-aware JSON requests; refreshes CSRF using the supplied client. */
public final class HttpTestRequests {
  private HttpTestRequests() {}
  public static java.net.http.HttpResponse<String> request(
      java.net.http.HttpClient client, tools.jackson.databind.ObjectMapper json, String base,
      String path, String method, Object body, boolean csrf) throws Exception {
    var request =
        java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + path))
            .header("Content-Type", "application/json");
    if (csrf) {
      var response =
          client.send(
              java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "auth/csrf"))
                  .GET()
                  .build(),
              java.net.http.HttpResponse.BodyHandlers.ofString());
      var token = json.readTree(response.body());
      request.header(token.get("headerName").asString(), token.get("token").asString());
    }
    return client.send(
        request
            .method(method, body == null ? java.net.http.HttpRequest.BodyPublishers.noBody()
                : java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build(),
        java.net.http.HttpResponse.BodyHandlers.ofString());
  }

}
