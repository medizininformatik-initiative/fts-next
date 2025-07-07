package care.smith.fts.util.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import care.smith.fts.util.auth.HttpClientAuth.Config;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

public class HttpClientBasicAuthTest {

  @Test
  public void deserialization() throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());

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
