package care.smith.fts.util.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

public class HTTPClientCookieTokenAuthTest {

  @Test
  public void deserialization() throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());

    var config =
        """
        cookieToken:
          token: token-090112
        """;

    assertThat(om.readValue(config, HttpClientAuthMethod.AuthMethod.class)).isNotNull();
  }

  @Test
  public void clientCreated() {
    HttpClientCookieTokenAuth config = new HttpClientCookieTokenAuth("token-090112");

    assertThatNoException().isThrownBy(() -> config.configure(WebClient.builder()));
  }
}
