package care.smith.fts.util.auth;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.auth.HttpClientAuth.Config;
import org.junit.jupiter.api.Test;

class HttpClientAuthTest {

  HttpClientBasicAuth.Config basic = new HttpClientBasicAuth.Config("username", "password");
  HttpClientOAuth2Auth.Config oauth2 = new HttpClientOAuth2Auth.Config("registration");
  HttpClientCookieTokenAuth.Config cookieToken = new HttpClientCookieTokenAuth.Config("cookie");

  @Test
  void fullConstructor() {
    var config = new Config(basic, oauth2, cookieToken);
    assertThat(config.basic()).isNotNull();
    assertThat(config.oauth2()).isNotNull();
    assertThat(config.cookieToken()).isNotNull();
  }

  @Test
  void basicConstructor() {
    var config = new Config(basic);
    assertThat(config.basic()).isNotNull();
    assertThat(config.oauth2()).isNull();
    assertThat(config.cookieToken()).isNull();
  }

  @Test
  void oauth2Constructor() {
    var config = new Config(oauth2);
    assertThat(config.basic()).isNull();
    assertThat(config.oauth2()).isNotNull();
    assertThat(config.cookieToken()).isNull();
  }

  @Test
  void cookieConstructor() {
    var config = new Config(cookieToken);
    assertThat(config.basic()).isNull();
    assertThat(config.oauth2()).isNull();
    assertThat(config.cookieToken()).isNotNull();
  }

  @Test
  void basicOauth2Constructor() {
    var config = new Config(basic, oauth2);
    assertThat(config.basic()).isNotNull();
    assertThat(config.oauth2()).isNotNull();
    assertThat(config.cookieToken()).isNull();
  }

  @Test
  void basicCookieConstructor() {
    var config = new Config(basic, cookieToken);
    assertThat(config.basic()).isNotNull();
    assertThat(config.oauth2()).isNull();
    assertThat(config.cookieToken()).isNotNull();
  }

  @Test
  void oauth2CookieVonstructor() {
    var config = new Config(oauth2, cookieToken);
    assertThat(config.basic()).isNull();
    assertThat(config.oauth2()).isNotNull();
    assertThat(config.cookieToken()).isNotNull();
  }
}
