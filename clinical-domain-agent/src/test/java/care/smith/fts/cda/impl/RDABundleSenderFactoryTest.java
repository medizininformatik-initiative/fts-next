package care.smith.fts.cda.impl;

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
class RDABundleSenderFactoryTest {

  @Autowired MeterRegistry meterRegistry;
  @Autowired WebClientSsl ssl;
  private final WebClient.Builder clientBuilder = WebClient.builder();
  private RDABundleSenderFactory factory;

  @BeforeEach
  void setUp() {
    factory = new RDABundleSenderFactory(clientBuilder, ssl, meterRegistry);
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
                new RDABundleSenderConfig(new HttpClientConfig("http://localhost"), "example")))
        .isNotNull();
  }
}
