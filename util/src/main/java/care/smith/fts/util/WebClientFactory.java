package care.smith.fts.util;

import static java.util.Optional.ofNullable;

import care.smith.fts.util.HttpClientConfig.Ssl;
import care.smith.fts.util.auth.HttpClientAuth;
import care.smith.fts.util.auth.HttpClientBasicAuth;
import care.smith.fts.util.auth.HttpClientCookieTokenAuth;
import care.smith.fts.util.auth.HttpClientOAuth2Auth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;

@Component
@Import({HttpClientBasicAuth.class, HttpClientOAuth2Auth.class, HttpClientCookieTokenAuth.class})
public class WebClientFactory {

  private final Builder clientBuilder;
  private final WebClientSsl ssl;
  private final HttpClientBasicAuth basic;
  private final HttpClientOAuth2Auth oauth2;
  private final HttpClientCookieTokenAuth token;

  public WebClientFactory(
      WebClient.Builder clientBuilder,
      WebClientSsl ssl,
      @Autowired(required = false) HttpClientBasicAuth basic,
      @Autowired(required = false) HttpClientOAuth2Auth oauth2,
      @Autowired(required = false) HttpClientCookieTokenAuth token) {
    this.clientBuilder = clientBuilder;
    this.ssl = ssl;
    this.basic = basic;
    this.oauth2 = oauth2;
    this.token = token;
  }

  public WebClient create(HttpClientConfig config) {
    return clientBuilder
        .baseUrl(config.baseUrl())
        .apply(b -> configureAuth(b, config.auth()))
        .apply(b -> configureSsl(b, config.ssl()))
        .build();
  }

  public WebClient create(Builder builder, HttpClientConfig config) {
    return builder
        .baseUrl(config.baseUrl())
        .apply(b -> configureAuth(b, config.auth()))
        .apply(b -> configureSsl(b, config.ssl()))
        .build();
  }

  private void configureAuth(Builder builder, HttpClientAuth.Config auth) {
    if (auth != null) {
      builder
          .apply(b -> configureAuth(b, basic, auth.basic()))
          .apply(b -> configureAuth(b, oauth2, auth.oauth2()))
          .apply(b -> configureAuth(b, token, auth.cookieToken()));
    }
  }

  private <C> void configureAuth(Builder b, HttpClientAuth<C> impl, C config) {
    ofNullable(config).ifPresent(c -> impl.configure(c, b));
  }

  private void configureSsl(Builder b, Ssl ssl) {
    ofNullable(ssl).ifPresent(s -> b.apply(this.ssl.fromBundle(s.bundle())));
  }
}
