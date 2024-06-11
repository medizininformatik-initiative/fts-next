package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.util.HTTPClientConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RDABundleSenderFactoryTest {

  private final HttpClientBuilder clientBuilder = HttpClientBuilder.create();
  private final FhirContext fhir = FhirContext.forR4();

  @Test
  void testConfigType() {
    assertThat(new RDABundleSenderFactory(clientBuilder, fhir).getConfigType()).isNotNull();
  }

  @Test
  void testCreate() {
    assertThat(
            new RDABundleSenderFactory(clientBuilder, fhir)
                .create(null, new RDABundleSenderConfig(new HTTPClientConfig("http://localhost"))))
        .isNotNull();
  }
}
