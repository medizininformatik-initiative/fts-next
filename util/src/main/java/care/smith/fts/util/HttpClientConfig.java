package care.smith.fts.util;

import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import care.smith.fts.util.auth.HttpClientAuthMethod.AuthMethod;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;
import org.springframework.web.reactive.function.client.WebClient;

public record HttpClientConfig(
    @NotBlank String baseUrl, @NotNull AuthMethod auth, @Nullable Ssl ssl) {

  public HttpClientConfig(@NotBlank String baseUrl, AuthMethod auth, Ssl ssl) {
    this.baseUrl = requireNonNull(emptyToNull(baseUrl), "Base URL must not be null");
    this.auth = ofNullable(auth).orElse(AuthMethod.NONE);
    this.ssl = ssl;
  }

  public HttpClientConfig(@NotBlank String baseUrl, AuthMethod auth) {
    this(baseUrl, auth, null);
  }

  public HttpClientConfig(@NotBlank String baseUrl) {
    this(baseUrl, AuthMethod.NONE);
  }

  public WebClient createClient(WebClient.Builder builder, WebClientSsl wcssl) {
    return builder
        .baseUrl(baseUrl())
        .apply(auth()::customize)
        .apply(b -> ofNullable(ssl()).ifPresent(s -> s.customize(b, wcssl)))
        .build();
  }

  public record Ssl(String bundle) {
    void customize(WebClient.Builder client, WebClientSsl wcssl) {
      client.apply(wcssl.fromBundle(bundle()));
    }
  }
}
