package care.smith.fts.util.auth;

import static org.springframework.http.HttpHeaders.COOKIE;

import org.springframework.web.reactive.function.client.WebClient;

public record HTTPClientCookieTokenAuth(
    /* */
    String token) implements HTTPClientAuthMethod {

  @Override
  public void configure(WebClient.Builder builder) {
    builder.defaultHeaders(h -> h.set(COOKIE, token));
  }
}
