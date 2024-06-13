package care.smith.fts.util;

import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import care.smith.fts.util.auth.HTTPClientAuthMethod;
import care.smith.fts.util.auth.HTTPClientAuthMethod.AuthMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.web.reactive.function.client.WebClient;

public record HTTPClientConfig(@NotBlank String baseUrl, @NotNull AuthMethod auth) {

  public HTTPClientConfig(@NotBlank String baseUrl, AuthMethod auth) {
    this.baseUrl = requireNonNull(emptyToNull(baseUrl), "Base URL must not be null");
    this.auth = ofNullable(auth).orElse(AuthMethod.NONE);
  }

  public HTTPClientConfig(@NotBlank String baseUrl) {
    this(baseUrl, AuthMethod.NONE);
  }

  public IGenericClient createClient(IRestfulClientFactory factory) {
    IGenericClient client = factory.newGenericClient(this.baseUrl());
    return configureAuth(client, authMethods(auth()));
  }

  private static Stream<HTTPClientAuthMethod> authMethods(AuthMethod auth) {
    return Stream.of(auth.basic(), auth.cookieToken(), auth.none()).filter(Objects::nonNull);
  }

  private static IGenericClient configureAuth(
      IGenericClient client, Stream<HTTPClientAuthMethod> authMethods) {
    authMethods.findFirst().ifPresent(a -> a.configure(client));
    return client;
  }

  public CloseableHttpClient createClient(HttpClientBuilder builder) {
    return configureAuth(builder, authMethods(auth())).build();
  }

  private static HttpClientBuilder configureAuth(
      HttpClientBuilder client, Stream<HTTPClientAuthMethod> authMethods) {
    authMethods.findFirst().ifPresent(a -> a.configure(client));
    return client;
  }

  private static WebClient.Builder configureAuth(
      WebClient.Builder builder, Stream<HTTPClientAuthMethod> authMethods) {
    authMethods.findFirst().ifPresent(a -> a.configure(builder));
    return builder;
  }

  public WebClient createClient(WebClient.Builder builder) {
    return configureAuth(builder, authMethods(auth())).build();
  }
}
