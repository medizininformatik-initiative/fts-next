package care.smith.fts.util.auth;

import static java.util.Map.of;
import static java.util.Objects.requireNonNull;

import lombok.Builder;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.web.reactive.function.client.WebClient;

public interface HttpClientAuth<T> {
  void configure(T config, WebClient.Builder builder);

  @Builder
  record Config(
      HttpClientBasicAuth.Config basic,
      HttpClientOAuth2Auth.Config oauth2,
      HttpClientCookieTokenAuth.Config cookieToken,
      Object none) {

    @ConstructorBinding
    public Config {}

    public Config(
        HttpClientBasicAuth.Config basic,
        HttpClientOAuth2Auth.Config oauth2,
        HttpClientCookieTokenAuth.Config cookieToken) {
      this(requireNonNull(basic), requireNonNull(oauth2), requireNonNull(cookieToken), of());
    }

    public Config(
        HttpClientOAuth2Auth.Config oauth2, HttpClientCookieTokenAuth.Config cookieToken) {
      this(null, requireNonNull(oauth2), requireNonNull(cookieToken), of());
    }

    public Config(HttpClientBasicAuth.Config basic, HttpClientCookieTokenAuth.Config cookieToken) {
      this(basic, null, requireNonNull(cookieToken), of());
    }

    public Config(HttpClientBasicAuth.Config basic, HttpClientOAuth2Auth.Config oauth2) {
      this(requireNonNull(basic), requireNonNull(oauth2), null, of());
    }

    public Config(HttpClientBasicAuth.Config basic) {
      this(requireNonNull(basic), null, null, of());
    }

    public Config(HttpClientCookieTokenAuth.Config cookieToken) {
      this(null, null, requireNonNull(cookieToken), of());
    }

    public Config(HttpClientOAuth2Auth.Config oauth2) {
      this(null, requireNonNull(oauth2), null, of());
    }
  }
}
