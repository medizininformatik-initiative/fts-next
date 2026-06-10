package care.smith.fts.util;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.web.reactive.function.client.WebClient.builder;
import static reactor.test.StepVerifier.create;

import care.smith.fts.util.HttpClientConfig.Redirects;
import care.smith.fts.util.HttpClientConfig.Ssl;
import care.smith.fts.util.auth.HttpClientAuth.Config;
import care.smith.fts.util.auth.HttpClientBasicAuth;
import care.smith.fts.util.auth.HttpClientCookieTokenAuth;
import care.smith.fts.util.auth.HttpClientOAuth2Auth;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

@Slf4j
@SpringBootTest
@WireMockTest
@ExtendWith(MockitoExtension.class)
class WebClientFactoryTest {

  WebClientFactory factory;
  ReactorResourceFactory resourceFactory;

  @BeforeEach
  void setUp(
      @Autowired ReactorResourceFactory resourceFactory,
      @Autowired SslBundles sslBundles,
      @Autowired(required = false) HttpClientBasicAuth basic,
      @Autowired(required = false) HttpClientCookieTokenAuth token) {
    log.info("WebClientFactoryTest setup");
    ReactiveOAuth2AuthorizedClientManager clientManager =
        mock(ReactiveOAuth2AuthorizedClientManager.class);
    var oauth2 = new HttpClientOAuth2Auth(clientManager);
    this.resourceFactory = resourceFactory;
    factory = new WebClientFactory(builder(), resourceFactory, sslBundles, basic, oauth2, token);
  }

  @Test
  void createWithoutAuth() {
    var config = new HttpClientConfig("http://localhost");
    assertThat(factory.create(config)).isNotNull();
  }

  @Test
  void createWithEmptyAuth() {
    var config = new HttpClientConfig("http://localhost");
    assertThat(factory.create(config)).isNotNull();
  }

  @Test
  void createWithBasicAuth() {
    var auth = new HttpClientBasicAuth.Config("user-144512", "pwd-144538");
    var config = new HttpClientConfig("http://localhost", new Config(auth));
    assertThat(factory.create(config)).isNotNull();
  }

  @Test
  void createWithMultipleAuthBasicIsTaken() {
    var basic = mock(HttpClientBasicAuth.class);
    var oauth2 = mock(HttpClientOAuth2Auth.class);
    var token = mock(HttpClientCookieTokenAuth.class);
    Builder builder = builder();
    var factory = new WebClientFactory(builder, resourceFactory, null, basic, oauth2, token);

    var basicConf = new HttpClientBasicAuth.Config("user-1505512", "pwd-15054518");
    var oauth2Conf = new HttpClientOAuth2Auth.Config("usr-142135");
    var tokenConf = new HttpClientCookieTokenAuth.Config("token-152510");
    var config =
        new HttpClientConfig("http://localhost", new Config(basicConf, oauth2Conf, tokenConf));

    assertThat(factory.create(config)).isNotNull();
    verify(basic).configure(eq(basicConf), any(WebClient.Builder.class));
    verify(oauth2, never()).configure(eq(oauth2Conf), any(WebClient.Builder.class));
    verify(token, never()).configure(eq(tokenConf), any(WebClient.Builder.class));
  }

  @Test
  void createWithBasicAuthMissingImplementation(@Autowired SslBundles sslBundles) {
    var factory = new WebClientFactory(builder(), resourceFactory, sslBundles, null, null, null);

    var auth = new HttpClientBasicAuth.Config("user-144512", "pwd-144538");
    var config = new HttpClientConfig("http://localhost", new Config(auth));
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> factory.create(config));
  }

  @Test
  void createWithOAuth2Auth() {
    var auth = new HttpClientOAuth2Auth.Config("usr-142135");
    var config = new HttpClientConfig("http://localhorst", new Config(auth));
    assertThat(factory.create(config)).isNotNull();
  }

  @Test
  void createWithOAuth2AuthMissingImplementation(@Autowired SslBundles sslBundles) {
    var factory = new WebClientFactory(builder(), resourceFactory, sslBundles, null, null, null);

    var auth = new HttpClientOAuth2Auth.Config("usr-142135");
    var config = new HttpClientConfig("http://localhost", new Config(auth));
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> factory.create(config));
  }

  @Test
  void createWithTokenAuth() {
    var auth = new HttpClientCookieTokenAuth.Config("token-146520");
    var config = new HttpClientConfig("http://localhost", new Config(auth));
    assertThat(factory.create(config)).isNotNull();
  }

  @Test
  void createWithTokenAuthMissingImplementation(@Autowired SslBundles sslBundles) {
    var factory = new WebClientFactory(builder(), resourceFactory, sslBundles, null, null, null);

    var auth = new HttpClientCookieTokenAuth.Config("token-146520");
    var config = new HttpClientConfig("http://localhost", new Config(auth));
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> factory.create(config));
  }

  @Test
  void createWithSsl() {
    var config = new HttpClientConfig("http://localhost", new Ssl("client"));
    log.debug("config: {}", config);
    assertThat(factory.create(config)).isNotNull();
  }

  @Test
  void createWithMissingSsl() {
    var config = new HttpClientConfig("http://localhost", new Ssl("missing-195151"));
    assertThatExceptionOfType(NoSuchSslBundleException.class)
        .isThrownBy(() -> factory.create(config));
  }

  @Test
  void defaultRedirectsFollow(
      @Autowired WebClient.Builder clientBuilder,
      @Autowired SslBundles sslBundles,
      WireMockRuntimeInfo wireMock) {
    var mock = wireMock.getWireMock();
    mock.register(
        get(urlEqualTo("/source"))
            .willReturn(aResponse().withStatus(307).withHeader("Location", "/target")));
    mock.register(
        get(urlEqualTo("/target")).willReturn(aResponse().withStatus(200).withBody("ok")));

    // No redirects configured -> defaults to FOLLOW_SAFE, so the 307 is followed transparently.
    var factory =
        new WebClientFactory(clientBuilder, resourceFactory, sslBundles, null, null, null);
    var client = factory.create(new HttpClientConfig(wireMock.getHttpBaseUrl()));

    create(client.get().uri("/source").retrieve().bodyToMono(String.class))
        .assertNext(s -> assertThat(s).isEqualTo("ok"))
        .verifyComplete();
  }

  @Test
  void dontFollowRedirectsSurfacesError(
      @Autowired WebClient.Builder clientBuilder,
      @Autowired SslBundles sslBundles,
      WireMockRuntimeInfo wireMock) {
    var mock = wireMock.getWireMock();
    mock.register(
        get(urlEqualTo("/source"))
            .willReturn(aResponse().withStatus(307).withHeader("Location", "/target")));

    // DONT_FOLLOW leaves the 307 unfollowed; WebClientDefaults turns it into an error rather than
    // letting the empty body pass through as a silent success (#1706).
    var factory =
        new WebClientFactory(clientBuilder, resourceFactory, sslBundles, null, null, null);
    var config = new HttpClientConfig(wireMock.getHttpBaseUrl(), null, null, Redirects.DONT_FOLLOW);
    var client = factory.create(config);

    create(client.get().uri("/source").retrieve().bodyToMono(String.class))
        .expectError(WebClientResponseException.class)
        .verify();
  }

  @Test
  void alwaysFollowFollowsRedirect(
      @Autowired WebClient.Builder clientBuilder,
      @Autowired SslBundles sslBundles,
      WireMockRuntimeInfo wireMock) {
    var mock = wireMock.getWireMock();
    mock.register(
        get(urlEqualTo("/source"))
            .willReturn(aResponse().withStatus(307).withHeader("Location", "/target")));
    mock.register(
        get(urlEqualTo("/target")).willReturn(aResponse().withStatus(200).withBody("ok")));

    var factory =
        new WebClientFactory(clientBuilder, resourceFactory, sslBundles, null, null, null);
    var config =
        new HttpClientConfig(wireMock.getHttpBaseUrl(), null, null, Redirects.ALWAYS_FOLLOW);
    var client = factory.create(config);

    create(client.get().uri("/source").retrieve().bodyToMono(String.class))
        .assertNext(s -> assertThat(s).isEqualTo("ok"))
        .verifyComplete();
  }

  // FOLLOW_SAFE's HTTPS->HTTP downgrade refusal (the only thing distinguishing it from
  // ALWAYS_FOLLOW) needs a real TLS upstream to exercise end-to-end, so the redirect predicate is
  // verified directly.
  @Test
  void safeRedirectRefusesHttpsToHttpDowngrade() {
    assertThat(safeRedirect("https://upstream/source", "http://other/target")).isFalse();
  }

  @Test
  void safeRedirectFollowsSameSchemeUpgradeAndRelative() {
    assertThat(safeRedirect("http://upstream/source", "http://other/target")).isTrue();
    assertThat(safeRedirect("https://upstream/source", "https://other/target")).isTrue();
    assertThat(safeRedirect("http://upstream/source", "https://other/target")).isTrue();
    assertThat(safeRedirect("https://upstream/source", "/target")).isTrue();
  }

  @Test
  void safeRedirectIgnoresNonRedirectStatus() {
    var response = mock(HttpClientResponse.class);
    when(response.status()).thenReturn(HttpResponseStatus.OK);

    assertThat(WebClientFactory.isSafeRedirect(mock(HttpClientRequest.class), response)).isFalse();
  }

  @Test
  void safeRedirectRefusesMissingLocationHeader() {
    var response = mock(HttpClientResponse.class);
    when(response.status()).thenReturn(HttpResponseStatus.TEMPORARY_REDIRECT);
    when(response.responseHeaders()).thenReturn(new DefaultHttpHeaders());

    assertThat(WebClientFactory.isSafeRedirect(mock(HttpClientRequest.class), response)).isFalse();
  }

  @Test
  void safeRedirectWithUnknownRequestUrlFollows() {
    // Without a request URL the downgrade check cannot apply, so the redirect is followed.
    assertThat(safeRedirect(null, "http://other/target")).isTrue();
  }

  private static boolean safeRedirect(String requestUrl, String location) {
    var request = mock(HttpClientRequest.class);
    var response = mock(HttpClientResponse.class);
    when(request.resourceUrl()).thenReturn(requestUrl);
    when(response.status()).thenReturn(HttpResponseStatus.TEMPORARY_REDIRECT);
    when(response.responseHeaders())
        .thenReturn(new DefaultHttpHeaders().set(HttpHeaderNames.LOCATION, location));
    return WebClientFactory.isSafeRedirect(request, response);
  }
}
