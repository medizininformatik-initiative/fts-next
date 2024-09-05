package care.smith.fts.util.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

public class HTTPClientBasicAuthTest {

  @Test
  public void deserialization() throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());

    var config =
        """
        basic:
          user: user-090058
          password: pass-090130
        """;

    assertThat(om.readValue(config, HttpClientAuthMethod.AuthMethod.class)).isNotNull();
  }

  @Test
  public void clientCreated() {
    HttpClientBasicAuth config = new HttpClientBasicAuth("user-090058", "pass-090130");

    var client = WebClient.builder();

    assertThatNoException().isThrownBy(() -> config.configure(client));
  }
}
