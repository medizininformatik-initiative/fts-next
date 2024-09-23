package care.smith.fts.util.auth;

import static org.springframework.http.HttpHeaders.COOKIE;

import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;
import org.springframework.web.reactive.function.client.WebClient;

public record HttpClientCookieTokenAuth(
    /* */
    String token) implements HttpClientAuthMethod {

  @Override
  public void configure(WebClient.Builder builder) {
    builder.defaultHeaders(h -> h.set(COOKIE, token));
  }
}
