package care.smith.fts.util.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.Test;

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

    assertThat(om.readValue(config, HTTPClientAuthMethod.AuthMethod.class)).isNotNull();
  }

  @Test
  public void hapiClientCreated() {
    HTTPClientBasicAuth config = new HTTPClientBasicAuth();
    config.setUser("user-090058");
    config.setPassword("pass-090130");

    IGenericClient client =
        FhirContext.forR4().getRestfulClientFactory().newGenericClient("http://localhost");

    assertThatNoException().isThrownBy(() -> config.configure(client));
  }

  @Test
  public void apacheClientCreated() {
    HTTPClientBasicAuth config = new HTTPClientBasicAuth();
    config.setUser("user-090058");
    config.setPassword("pass-090130");

    assertThatNoException().isThrownBy(() -> config.configure(HttpClientBuilder.create()));
  }
}
