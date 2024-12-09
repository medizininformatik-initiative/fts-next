package care.smith.fts.util.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.web.reactive.function.client.WebClient.builder;

import care.smith.fts.util.auth.HttpClientAuth.Config;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

public class HTTPClientCookieTokenAuthTest {

  @Test
  public void deserialization() throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());

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
