package care.smith.fts.util.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import care.smith.fts.util.auth.HttpServerAuthMethod.AuthMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

class HttpServerAuthConfigTest {

  @Test
  void noUserDetailsForNoneAuth() {
    var config = new HttpServerAuthConfig();

    var reactiveUserDetailsService = config.userDetailsService();

    assertThat(reactiveUserDetailsService).isNull();
  }

  @Test
  void multipleAuthMethodsThrow() {
    var config = new HttpServerAuthConfig();
    config.setAuth(
        new AuthMethod(
            new HttpServerBasicAuth(List.of()), new HttpServerNoneAuth()));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(config::userDetailsService)
        .withMessageContainingAll("Multiple", "Auth methods", "found");
  }

  @Test
  void noAuthMethodDefaultsToNone() {
    var config = new HttpServerAuthConfig();
    config.setAuth(new AuthMethod(null, null));

    var reactiveUserDetailsService = config.userDetailsService();

    assertThat(reactiveUserDetailsService).isNull();
  }
}
