package care.smith.fts.util;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNullElse;

import care.smith.fts.util.HttpClientConfig.Redirects;
import care.smith.fts.util.HttpClientConfig.Ssl;
import care.smith.fts.util.auth.HttpClientAuth;
import care.smith.fts.util.auth.HttpClientBasicAuth;
import care.smith.fts.util.auth.HttpClientCookieTokenAuth;
import care.smith.fts.util.auth.HttpClientOAuth2Auth;
import io.netty.handler.codec.http.HttpHeaderNames;
import jakarta.annotation.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorBuilder;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

@Slf4j
@Component
@Import({HttpClientBasicAuth.class, HttpClientOAuth2Auth.class, HttpClientCookieTokenAuth.class})
public class WebClientFactory {

  private static final Set<Integer> REDIRECT_CODES = Set.of(301, 302, 303, 307, 308);

  private final Builder clientBuilder;
  private final ReactorResourceFactory resourceFactory;
  private final SslBundles sslBundles;
  private final HttpClientBasicAuth basic;
  private final HttpClientOAuth2Auth oauth2;
  private final HttpClientCookieTokenAuth token;

  public WebClientFactory(
      WebClient.Builder clientBuilder,
      ReactorResourceFactory resourceFactory,
      SslBundles sslBundles,
      @Autowired(required = false) HttpClientBasicAuth basic,
      @Autowired(required = false) HttpClientOAuth2Auth oauth2,
      @Autowired(required = false) HttpClientCookieTokenAuth token) {
    this.clientBuilder = clientBuilder;
    this.resourceFactory = resourceFactory;
    this.sslBundles = sslBundles;
    this.basic = basic;
    this.oauth2 = oauth2;
    this.token = token;
  }

  public WebClient create(HttpClientConfig config) {
    log.debug("Webclient Config {}", config);
    return clientBuilder
        .clone()
        .clientConnector(connector(config))
        .baseUrl(config.baseUrl())
        .apply(b -> configureAuth(b, config.auth()))
        .build();
  }

  /**
   * Builds the per-upstream connector. Redirect policy, SSL bundle and connect timeout are composed
   * onto a single Reactor Netty client here so each {@link HttpClientConfig} controls its own
   * redirect behaviour, while {@code withReactorResourceFactory} keeps every upstream on the shared
   * connection pool from {@code AgentConfiguration#ftsClientResources} (#1729). The redirect policy
   * is applied via an http-client customizer (which runs last and so overrides Spring's
   * settings-based default), keeping the underlying client API out of the public config. A {@code
   * null} {@code redirects} defaults to {@link Redirects#FOLLOW_SAFE}.
   */
  private ClientHttpConnector connector(HttpClientConfig config) {
    var settings =
        HttpClientSettings.defaults()
            .withConnectTimeout(ofSeconds(10))
            .withSslBundle(sslBundle(config.ssl()));
    return ClientHttpConnectorBuilder.reactor()
        .withReactorResourceFactory(resourceFactory)
        .withHttpClientCustomizer(client -> applyRedirects(client, config.redirects()))
        .build(settings);
  }

  /**
   * Translates the public {@link Redirects} policy to the Reactor Netty redirect configuration.
   * {@code null} defaults to {@link Redirects#FOLLOW_SAFE}, which follows via {@link
   * #isSafeRedirect} because Reactor Netty's boolean {@code followRedirect} cannot refuse
   * HTTPS&rarr;HTTP downgrades on its own.
   */
  static HttpClient applyRedirects(HttpClient client, @Nullable Redirects redirects) {
    return switch (requireNonNullElse(redirects, Redirects.FOLLOW_SAFE)) {
      case FOLLOW_SAFE -> client.followRedirect(WebClientFactory::isSafeRedirect);
      case ALWAYS_FOLLOW -> client.followRedirect(true);
      case DONT_FOLLOW -> client.followRedirect(false);
    };
  }

  /**
   * Follow a 3xx unless it is an HTTPS&rarr;HTTP downgrade, mirroring the JDK's {@code
   * Redirect.NORMAL}. A 3xx without a {@code Location} header is not followed, and a request whose
   * URL is unknown cannot be checked for a downgrade, so its redirect is followed. A refused
   * downgrade leaves the 3xx to reach WebClient, where {@link WebClientDefaults} turns it into an
   * error (#1706).
   */
  static boolean isSafeRedirect(HttpClientRequest request, HttpClientResponse response) {
    return REDIRECT_CODES.contains(response.status().code())
        && Optional.ofNullable(response.responseHeaders().get(HttpHeaderNames.LOCATION))
            .filter(location -> !isHttpsToHttpDowngrade(request.resourceUrl(), location))
            .isPresent();
  }

  private static boolean isHttpsToHttpDowngrade(@Nullable String requestUrl, String location) {
    return location.startsWith("http://")
        && Optional.ofNullable(requestUrl).filter(url -> url.startsWith("https://")).isPresent();
  }

  private @Nullable SslBundle sslBundle(@Nullable Ssl ssl) {
    return Optional.ofNullable(ssl).map(s -> sslBundles.getBundle(s.bundle())).orElse(null);
  }

  private void configureAuth(Builder builder, @Nullable HttpClientAuth.Config auth) {
    Optional.ofNullable(auth)
        .flatMap(
            a ->
                configurer("basic", basic, a.basic())
                    .or(() -> configurer("oauth2", oauth2, a.oauth2()))
                    .or(() -> configurer("cookieToken", token, a.cookieToken())))
        .ifPresent(configurer -> configurer.accept(builder));
  }

  private static <C> Optional<Consumer<Builder>> configurer(
      String name, @Nullable HttpClientAuth<C> impl, @Nullable C config) {
    return Optional.ofNullable(config).map(c -> b -> requireImpl(name, impl).configure(c, b));
  }

  private static <C> HttpClientAuth<C> requireImpl(String name, @Nullable HttpClientAuth<C> impl) {
    return Optional.ofNullable(impl)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Cannot configure %s authentication, missing implementing class."
                        .formatted(name)));
  }
}
