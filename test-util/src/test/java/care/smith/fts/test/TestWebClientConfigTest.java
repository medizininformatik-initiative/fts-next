package care.smith.fts.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import care.smith.fts.util.auth.HttpClientBasicAuth.Config;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

public class TestWebClientConfigTest {

  @Test
  public void deserializationWithoutAuth() throws JacksonException {
    ObjectMapper om = YAMLMapper.builder().build();

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
  public void deserializationWithBasicAuth() throws JacksonException {
    ObjectMapper om = YAMLMapper.builder().build();

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
