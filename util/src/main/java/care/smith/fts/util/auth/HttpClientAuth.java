package care.smith.fts.util.auth;

import org.springframework.web.reactive.function.client.WebClient.Builder;

public interface HttpClientAuth<T> {
  void configure(T config, Builder builder);

  record Config(
      HttpClientBasicAuth.Config basic,
      HttpClientOAuth2Auth.Config oauth2,
      HttpClientCookieTokenAuth.Config cookieToken) {
  }
}
