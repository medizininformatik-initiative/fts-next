package care.smith.fts.util.auth;

import static java.util.Map.of;

import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient.Builder;

public interface HttpClientAuth<T> {
  void configure(T config, Builder builder);

  record Config(
      HttpClientBasicAuth.Config basic,
      HttpClientCookieTokenAuth.Config cookieToken,
      Map<Object, Object> none) {

    public Config(HttpClientBasicAuth.Config basic, HttpClientCookieTokenAuth.Config cookieToken) {
      this(basic, cookieToken, of());
    }
  }
}
