package care.smith.fts.util.auth;

import care.smith.fts.util.auth.HttpClientBasicAuth.Config;
import org.springframework.web.reactive.function.client.WebClient;

public class HttpClientBasicAuth implements HttpClientAuth<Config> {

  public record Config(
      /* */
      String user,

      /* */
      String password) {}

  @Override
  public void configure(Config config, WebClient.Builder builder) {
    builder.defaultHeaders(headers -> headers.setBasicAuth(config.user(), config.password()));
  }
}
