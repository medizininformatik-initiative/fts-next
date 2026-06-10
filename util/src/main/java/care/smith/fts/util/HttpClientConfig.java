package care.smith.fts.util;

import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;

import care.smith.fts.util.auth.HttpClientAuth;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

public record HttpClientConfig(
    @NotBlank String baseUrl,
    @Nullable HttpClientAuth.Config auth,
    @Nullable Ssl ssl,
    @Nullable Redirects redirects) {

  @ConstructorBinding
  public HttpClientConfig(
      @NotBlank String baseUrl, HttpClientAuth.Config auth, Ssl ssl, Redirects redirects) {
    this.baseUrl = requireNonNull(emptyToNull(baseUrl), "Base URL must not be null");
    this.auth = auth;
    this.ssl = ssl;
    this.redirects = redirects;
  }

  public HttpClientConfig(@NotBlank String baseUrl, HttpClientAuth.Config auth, Ssl ssl) {
    this(baseUrl, auth, ssl, null);
  }

  public HttpClientConfig(@NotBlank String baseUrl, HttpClientAuth.Config auth) {
    this(
        requireNonNull(emptyToNull(baseUrl)),
        requireNonNull(auth, "auth must not be null"),
        null,
        null);
  }

  public HttpClientConfig(@NotBlank String baseUrl, Ssl ssl) {
    this(
        requireNonNull(emptyToNull(baseUrl)),
        null,
        requireNonNull(ssl, "ssl must not be null"),
        null);
  }

  public HttpClientConfig(@NotBlank String baseUrl) {
    this(requireNonNull(emptyToNull(baseUrl)), null, null, null);
  }

  public record Ssl(String bundle) {}

  /**
   * Per-upstream redirect-following policy. Names describe intent and deliberately hide the
   * underlying client's API so config is decoupled from the HTTP library. {@link WebClientFactory}
   * translates these to the Reactor Netty redirect configuration.
   */
  public enum Redirects {
    /** Follow 3xx redirects, but refuse HTTPS&rarr;HTTP downgrades. The default. */
    FOLLOW_SAFE,
    /**
     * Follow all 3xx redirects, including HTTPS&rarr;HTTP downgrades. A downgrade exposes
     * credentials and data over plaintext, so only use this for a trusted upstream that genuinely
     * redirects across schemes.
     */
    ALWAYS_FOLLOW,
    /** Do not follow redirects; an unfollowed 3xx surfaces as an error. */
    DONT_FOLLOW
  }
}
