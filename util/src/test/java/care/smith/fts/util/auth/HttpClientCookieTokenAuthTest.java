package care.smith.fts.util.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.web.reactive.function.client.WebClient.builder;

import care.smith.fts.util.auth.HttpClientAuth.Config;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

public class HttpClientCookieTokenAuthTest {

  @Test
  public void deserialization() throws JacksonException {
    ObjectMapper om = YAMLMapper.builder().build();

    var config =
        """
        cookieToken:
          token: token-090112
        """;

    assertThat(om.readValue(config, Config.class)).isNotNull();
  }

  @Test
  public void clientCreated() {
    var impl = new HttpClientCookieTokenAuth();

    assertThatNoException()
        .isThrownBy(
            () -> impl.configure(new HttpClientCookieTokenAuth.Config("token-090112"), builder()));
  }
}
