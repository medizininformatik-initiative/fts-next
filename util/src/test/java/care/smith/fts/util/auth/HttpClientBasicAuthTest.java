package care.smith.fts.util.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import care.smith.fts.util.auth.HttpClientAuth.Config;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

public class HttpClientBasicAuthTest {

  @Test
  public void deserialization() throws JacksonException {
    ObjectMapper om = YAMLMapper.builder().build();

    var config =
        """
        basic:
          user: user-090058
          password: pass-090130
        """;

    assertThat(om.readValue(config, Config.class)).isNotNull();
  }

  @Test
  public void clientCreated() {
    var config = new HttpClientBasicAuth.Config("user-090058", "pass-090130");
    var auth = new HttpClientBasicAuth();

    var client = WebClient.builder();

    assertThatNoException().isThrownBy(() -> auth.configure(config, client));
  }
}
