package care.smith.fts.util;

import static org.assertj.core.api.Assertions.*;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import care.smith.fts.util.auth.HTTPClientAuthMethod;
import care.smith.fts.util.auth.HTTPClientAuthMethod.AuthMethod;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.Test;

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
    assertThat(new HTTPClientConfig("http://localhost", null).auth())
        .isEqualTo(AuthMethod.NONE);
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
  public void hapiClientCreated() {
    HTTPClientConfig config = new HTTPClientConfig("http://localhost");
    IGenericClient client = config.createClient(FhirContext.forR4().getRestfulClientFactory());

    assertThat(client).isNotNull();
  }

  @Test
  public void apacheClientCreated() {
    HTTPClientConfig config = new HTTPClientConfig("http://localhost");
    CloseableHttpClient client = config.createClient(HttpClientBuilder.create());

    assertThat(client).isNotNull();
  }
}
