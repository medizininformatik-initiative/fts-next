package care.smith.fts.util.auth;

import org.springframework.web.reactive.function.client.WebClient;

public record HttpClientBasicAuth(
    /* */
    String user,

    /* */
    String password)
    implements HttpClientAuthMethod {

  @Override
  public void configure(WebClient.Builder builder) {
    builder.defaultHeaders(headers -> headers.setBasicAuth(user, password));
  }
}
