package care.smith.fts.util;

import static org.assertj.core.api.Assertions.*;

import care.smith.fts.util.auth.HTTPClientAuthMethod.AuthMethod;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

public class HTTPClientConfigTest {

  @Test
  public void nullBaseUrlThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new HTTPClientConfig(null));
  }

  @Test
  public void emptyBaseUrlThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new HTTPClientConfig(""));
  }

  @Test
  public void nullAuthDoesntThrow() {
    assertThat(new HTTPClientConfig("http://localhost", null).auth()).isEqualTo(AuthMethod.NONE);
  }

  @Test
  public void deserialization() throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());

    var config =
        """
        baseUrl: "http://localhost"
        auth:
          none: {}
        """;

    assertThat(om.readValue(config, HTTPClientConfig.class)).isNotNull();
  }

  @Test
  public void clientCreated() {
    HTTPClientConfig config = new HTTPClientConfig("http://localhost");
    WebClient client = config.createClient(WebClient.builder());

    assertThat(client).isNotNull();
  }
}
