package care.smith.fts.rda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.HTTPClientConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
class FhirStoreBundleSenderFactoryTest {

  @Autowired MeterRegistry meterRegistry;

  private final WebClient.Builder clientBuilder = WebClient.builder();

  @Test
  void testConfigType() {
    assertThat(new FhirStoreBundleSenderFactory(clientBuilder, meterRegistry).getConfigType())
        .isNotNull();
  }

  @Test
  void testCreate() {
    assertThat(
            new FhirStoreBundleSenderFactory(clientBuilder, meterRegistry)
                .create(
                    null,
                    new FhirStoreBundleSenderConfig(
                        new HTTPClientConfig("http://localhost"), "example")))
        .isNotNull();
  }
}
