package care.smith.fts.util.auth;

import org.springframework.web.reactive.function.client.WebClient;

public record HTTPClientBasicAuth(
    /* */
    String user,

    /* */
    String password)
    implements HTTPClientAuthMethod {

  @Override
  public void configure(WebClient.Builder builder) {
    builder.defaultHeaders(headers -> headers.setBasicAuth(user, password));
  }
}
