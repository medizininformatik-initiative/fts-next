package care.smith.fts.util;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

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
  public void emptyAuthDoesntThrow() {
    assertThatNoException().isThrownBy(() -> new HttpClientConfig("http://localhost", null, null));
  }

  @Test
  public void emptySslDoesntThrow() {
    assertThatNoException().isThrownBy(() -> new HttpClientConfig("http://localhost", null, null));
  }

  @Test
  public void deserializationWithoutAuth() throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());

    var config =
        """
        baseUrl: "http://localhost"
        """;

    assertThat(om.readValue(config, HttpClientConfig.class)).isNotNull();
  }

  @Test
  public void deserializationWithEmptyAuth() throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());

    var config =
        """
        baseUrl: "http://localhost"
        auth: {}
        """;

    assertThat(om.readValue(config, HttpClientConfig.class)).isNotNull();
  }

  @Test
  public void deserializationWithNoneAuth() throws JsonProcessingException {
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
  public void deserializationWithAuth() throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());

    var config =
        """
        baseUrl: "http://localhost"
        auth:
          basic:
            user: foo
            password: bar
        """;

    assertThat(om.readValue(config, HttpClientConfig.class)).isNotNull();
  }
}
