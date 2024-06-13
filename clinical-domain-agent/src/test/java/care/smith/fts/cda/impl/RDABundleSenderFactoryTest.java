package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.util.HTTPClientConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
class RDABundleSenderFactoryTest {

  private final WebClient.Builder clientBuilder = WebClient.builder();
  private final FhirContext fhir = FhirContext.forR4();

  @Test
  void testConfigType() {
    assertThat(new RDABundleSenderFactory(clientBuilder).getConfigType()).isNotNull();
  }

  @Test
  void testCreate() {
    assertThat(
            new RDABundleSenderFactory(clientBuilder)
                .create(
                    null,
                    new RDABundleSenderConfig(new HTTPClientConfig("http://localhost"), "example")))
        .isNotNull();
  }
}
