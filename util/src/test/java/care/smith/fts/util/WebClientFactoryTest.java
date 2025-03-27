package care.smith.fts.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.web.reactive.function.client.WebClient.builder;

import care.smith.fts.util.HttpClientConfig.Ssl;
import care.smith.fts.util.auth.HttpClientAuth.Config;
import care.smith.fts.util.auth.HttpClientBasicAuth;
import care.smith.fts.util.auth.HttpClientCookieTokenAuth;
import care.smith.fts.util.auth.HttpClientOAuth2Auth;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;

@Slf4j
@SpringBootTest
@ExtendWith(MockitoExtension.class)
class WebClientFactoryTest {

  WebClientFactory factory;

  @BeforeEach
  void setUp(
      @Autowired WebClientSsl ssl,
      @Autowired(required = false) HttpClientBasicAuth basic,
      @Autowired(required = false) HttpClientCookieTokenAuth token) {
    log.info("WebClientFactoryTest setup");
    ReactiveOAuth2AuthorizedClientManager clientManager =
        mock(ReactiveOAuth2AuthorizedClientManager.class);
    var oauth2 = new HttpClientOAuth2Auth(clientManager);
    factory = new WebClientFactory(builder(), ssl, basic, oauth2, token);
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
    var factory = new WebClientFactory(builder, null, basic, oauth2, token);

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
  void createWithBasicAuthMissingImplementation(@Autowired WebClientSsl ssl) {
    var factory = new WebClientFactory(builder(), ssl, null, null, null);

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
  void createWithOAuth2AuthMissingImplementation(@Autowired WebClientSsl ssl) {
    var factory = new WebClientFactory(builder(), ssl, null, null, null);

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
  void createWithTokenAuthMissingImplementation(@Autowired WebClientSsl ssl) {
    var factory = new WebClientFactory(builder(), ssl, null, null, null);

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
}
