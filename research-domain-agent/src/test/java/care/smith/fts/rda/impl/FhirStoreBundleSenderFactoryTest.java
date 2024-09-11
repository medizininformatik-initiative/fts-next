package care.smith.fts.rda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.HttpClientConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
class FhirStoreBundleSenderFactoryTest {

  @Autowired MeterRegistry meterRegistry;
  @Autowired WebClientSsl ssl;

  private final WebClient.Builder clientBuilder = WebClient.builder();
  private FhirStoreBundleSenderFactory factory;

  @BeforeEach
  void setUp() {
    factory = new FhirStoreBundleSenderFactory(clientBuilder, ssl, meterRegistry);
  }

  @Test
  void testConfigType() {
    assertThat(factory.getConfigType()).isNotNull();
  }

  @Test
  void testCreate() {
    assertThat(
            factory.create(
                null,
                new FhirStoreBundleSenderConfig(
                    new HttpClientConfig("http://localhost"), "example")))
        .isNotNull();
  }
}
