package care.smith.fts.util;

import static org.assertj.core.api.Assertions.*;

import care.smith.fts.util.auth.HttpClientAuthMethod.AuthMethod;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

public class HttpClientConfigTest {

  @Test
  public void nullBaseUrlThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new HttpClientConfig(null));
  }

  @Test
  public void emptyBaseUrlThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new HttpClientConfig(""));
  }

  @Test
  public void nullAuthDoesntThrow() {
    assertThat(new HttpClientConfig("http://localhost", null).auth()).isEqualTo(AuthMethod.NONE);
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

    assertThat(om.readValue(config, HttpClientConfig.class)).isNotNull();
  }

  @Test
  public void clientCreated() {
    HttpClientConfig config = new HttpClientConfig("http://localhost");
    WebClient client = config.createClient(WebClient.builder());

    assertThat(client).isNotNull();
  }
}
