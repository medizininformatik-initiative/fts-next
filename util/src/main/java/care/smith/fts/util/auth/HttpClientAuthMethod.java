package care.smith.fts.util.auth;

import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.web.reactive.function.client.WebClient;

public interface HttpClientAuthMethod {
  void configure(WebClient.Builder builder);

  record AuthMethod(
      HttpClientBasicAuth basic,
      HttpClientCookieTokenAuth cookieToken,
      HttpClientNoneAuth none) {

    public static AuthMethod NONE = new AuthMethod(null, null, HttpClientNoneAuth.NONE);

    public void customize(WebClient.Builder builder) {
      Stream.of(basic(), cookieToken(), none())
          .filter(Objects::nonNull)
          .findFirst()
          .orElse(none())
          .configure(builder);
    }
  }
}
