package care.smith.fts.rda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.HTTPClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class FhirStoreBundleSenderFactoryTest {

  private final WebClient.Builder clientBuilder = WebClient.builder();

  @Test
  void testConfigType() {
    assertThat(new FhirStoreBundleSenderFactory(clientBuilder).getConfigType()).isNotNull();
  }

  @Test
  void testCreate() {
    assertThat(
            new FhirStoreBundleSenderFactory(clientBuilder)
                .create(
                    null,
                    new FhirStoreBundleSenderConfig(
                        new HTTPClientConfig("http://localhost"), "example")))
        .isNotNull();
  }
}
