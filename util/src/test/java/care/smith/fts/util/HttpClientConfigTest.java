package care.smith.fts.util;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

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
  public void deserializationWithoutAuth() throws JacksonException {
    ObjectMapper om = YAMLMapper.builder().build();

    var config =
        """
        baseUrl: "http://localhost"
        """;

    assertThat(om.readValue(config, HttpClientConfig.class)).isNotNull();
  }

  @Test
  public void deserializationWithEmptyAuth() throws JacksonException {
    ObjectMapper om = YAMLMapper.builder().build();

    var config =
        """
        baseUrl: "http://localhost"
        auth: {}
        """;

    assertThat(om.readValue(config, HttpClientConfig.class)).isNotNull();
  }

  @Test
  public void deserializationWithNoneAuth() throws JacksonException {
    ObjectMapper om = YAMLMapper.builder().build();

    var config =
        """
        baseUrl: "http://localhost"
        auth:
          none: {}
        """;

    assertThat(om.readValue(config, HttpClientConfig.class)).isNotNull();
  }

  @Test
  public void deserializationWithAuth() throws JacksonException {
    ObjectMapper om = YAMLMapper.builder().build();

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
