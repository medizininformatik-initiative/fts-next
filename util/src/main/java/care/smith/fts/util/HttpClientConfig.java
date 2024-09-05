package care.smith.fts.util;

import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import care.smith.fts.util.auth.HttpClientAuthMethod;
import care.smith.fts.util.auth.HttpClientAuthMethod.AuthMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.web.reactive.function.client.WebClient;

public record HttpClientConfig(@NotBlank String baseUrl, @NotNull AuthMethod auth) {

  public HttpClientConfig(@NotBlank String baseUrl, AuthMethod auth) {
    this.baseUrl = requireNonNull(emptyToNull(baseUrl), "Base URL must not be null");
    this.auth = ofNullable(auth).orElse(AuthMethod.NONE);
  }

  public HttpClientConfig(@NotBlank String baseUrl) {
    this(baseUrl, AuthMethod.NONE);
  }

  private static Stream<HttpClientAuthMethod> authMethods(AuthMethod auth) {
    return Stream.of(auth.basic(), auth.cookieToken(), auth.none()).filter(Objects::nonNull);
  }

  private static WebClient.Builder configureAuth(
      WebClient.Builder builder, Stream<HttpClientAuthMethod> authMethods) {
    authMethods.findFirst().ifPresent(a -> a.configure(builder));
    return builder;
  }

  public WebClient createClient(WebClient.Builder builder) {
    return configureAuth(builder, authMethods(auth())).baseUrl(baseUrl).build();
  }
}
