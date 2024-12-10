package care.smith.fts.util.auth;

import static org.springframework.http.HttpHeaders.COOKIE;

import care.smith.fts.util.auth.HttpClientCookieTokenAuth.Config;
import org.springframework.web.reactive.function.client.WebClient;

public class HttpClientCookieTokenAuth implements HttpClientAuth<Config> {

  public record Config(
      /* */
      String token) {}

  @Override
  public void configure(Config config, WebClient.Builder builder) {
    builder.defaultHeaders(h -> h.set(COOKIE, config.token()));
  }
}
