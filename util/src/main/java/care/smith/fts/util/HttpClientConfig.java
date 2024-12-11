package care.smith.fts.util;

import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;

import care.smith.fts.util.auth.HttpClientAuth;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record HttpClientConfig(
    @NotBlank String baseUrl, @Nullable HttpClientAuth.Config auth, @Nullable Ssl ssl) {

  public HttpClientConfig(@NotBlank String baseUrl, HttpClientAuth.Config auth, Ssl ssl) {
    log.debug("Auth: {}", auth);
    this.baseUrl = requireNonNull(emptyToNull(baseUrl), "Base URL must not be null");
    this.auth = auth;
    this.ssl = ssl;
  }

  public HttpClientConfig(@NotBlank String baseUrl, HttpClientAuth.Config auth) {
    this(requireNonNull(emptyToNull(baseUrl)), requireNonNull(auth, "auth must not be null"), null);
  }

  public HttpClientConfig(@NotBlank String baseUrl, Ssl ssl) {
    this(requireNonNull(emptyToNull(baseUrl)), null, requireNonNull(ssl, "ssl must not be null"));
  }

  public HttpClientConfig(@NotBlank String baseUrl) {
    this(requireNonNull(emptyToNull(baseUrl)), null, null);
  }

  public record Ssl(String bundle) {}
}
