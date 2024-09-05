package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.HttpClientConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
class RDABundleSenderFactoryTest {

  @Autowired MeterRegistry meterRegistry;
  private final WebClient.Builder clientBuilder = WebClient.builder();

  @Test
  void testConfigType() {
    assertThat(new RDABundleSenderFactory(clientBuilder, meterRegistry).getConfigType())
        .isNotNull();
  }

  @Test
  void testCreate() {
    assertThat(
            new RDABundleSenderFactory(clientBuilder, meterRegistry)
                .create(
                    null,
                    new RDABundleSenderConfig(new HttpClientConfig("http://localhost"), "example")))
        .isNotNull();
  }
}
