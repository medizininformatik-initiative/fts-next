package care.smith.fts.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import care.smith.fts.util.auth.HttpClientBasicAuth.Config;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

public class TestWebClientConfigTest {

  @Test
  public void deserializationWithoutAuth() throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());

    var config =
        """
        webclient:
          cd-agent:
            ssl:
              bundle: default
        """;

    var actual = om.readValue(config, TestWebClientConfig.class);
    assertThatNoException().isThrownBy(() -> actual.findConfigurationEntry("cd-agent"));
  }

  @Test
  public void deserializationWithBasicAuth() throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());

    var config =
        """
        webclient:
          foo-095122:
            auth:
              none: {}
              basic:
                user: user-095101
                password: pass-095200
        """;

    var actual = om.readValue(config, TestWebClientConfig.class);
    assertThat(actual.findConfigurationEntry("foo-095122"))
        .isNotNull()
        .hasValueSatisfying(
            entry -> {
              assertThat(entry.auth()).isNotNull();
              assertThat(entry.auth().basic()).isEqualTo(new Config("user-095101", "pass-095200"));
              assertThat(entry.ssl()).isNull();
            });
  }
}
