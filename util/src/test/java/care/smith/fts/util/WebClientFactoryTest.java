package care.smith.fts.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.web.reactive.function.client.WebClient.builder;

import care.smith.fts.util.HttpClientConfig.Ssl;
import care.smith.fts.util.auth.HttpClientAuth.Config;
import care.smith.fts.util.auth.HttpClientBasicAuth;
import care.smith.fts.util.auth.HttpClientCookieTokenAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient.Builder;

@SpringBootTest
class WebClientFactoryTest {

  WebClientFactory factory;

  @BeforeEach
  void setUp(
      @Autowired WebClientSsl ssl,
      @Autowired HttpClientBasicAuth basic,
      @Autowired HttpClientCookieTokenAuth token) {
    factory = new WebClientFactory(builder(), ssl, basic, token);
  }

  @Test
  void createWithoutAuth() {
    var config = new HttpClientConfig("http://localhost");
    assertThat(factory.create(config)).isNotNull();
  }

  @Test
  void createWithEmptyAuth() {
    var config = new HttpClientConfig("http://localhost", new Config(null, null));
    assertThat(factory.create(config)).isNotNull();
  }

  @Test
  void createWithBasicAuth() {
    var auth = new HttpClientBasicAuth.Config("user-144512", "pwd-144538");
    var config = new HttpClientConfig("http://localhost", new Config(auth, null));
    assertThat(factory.create(config)).isNotNull();
  }

  @Test
  void createWithMultipleAuthBasicIsTaken() {
    var basic = Mockito.mock(HttpClientBasicAuth.class);
    var token = Mockito.mock(HttpClientCookieTokenAuth.class);
    Builder builder = builder();
    factory = new WebClientFactory(builder, null, basic, token);

    var basicConf = new HttpClientBasicAuth.Config("user-1505512", "pwd-15054518");
    var tokenConf = new HttpClientCookieTokenAuth.Config("token-152510");
    var config = new HttpClientConfig("http://localhost", new Config(basicConf, tokenConf));

    assertThat(factory.create(builder, config)).isNotNull();
    verify(basic).configure(basicConf, builder);
    verify(token, never()).configure(tokenConf, builder);
  }

  @Test
  void createWithBasicAuthMissingImplementation(
      @Autowired WebClientSsl ssl, @Autowired HttpClientCookieTokenAuth token) {
    factory = new WebClientFactory(builder(), ssl, null, token);

    var auth = new HttpClientBasicAuth.Config("user-144512", "pwd-144538");
    var config = new HttpClientConfig("http://localhost", new Config(auth, null));
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> factory.create(config));
  }

  @Test
  void createWithTokenAuth() {
    var auth = new HttpClientCookieTokenAuth.Config("token-146520");
    var config = new HttpClientConfig("http://localhost", new Config(null, auth));
    assertThat(factory.create(config)).isNotNull();
  }

  @Test
  void createWithTokenAuthMissingImplementation(
      @Autowired WebClientSsl ssl, @Autowired HttpClientBasicAuth basic) {
    factory = new WebClientFactory(builder(), ssl, basic, null);

    var auth = new HttpClientCookieTokenAuth.Config("token-146520");
    var config = new HttpClientConfig("http://localhost", new Config(null, auth));
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> factory.create(config));
  }

  @Test
  void createWithSsl() {
    var config = new HttpClientConfig("http://localhost", null, new Ssl("client"));
    assertThat(factory.create(config)).isNotNull();
  }

  @Test
  void createWithMissingSsl() {
    var config = new HttpClientConfig("http://localhost", null, new Ssl("missing-195151"));
    assertThatExceptionOfType(NoSuchSslBundleException.class)
        .isThrownBy(() -> factory.create(config));
  }
}
