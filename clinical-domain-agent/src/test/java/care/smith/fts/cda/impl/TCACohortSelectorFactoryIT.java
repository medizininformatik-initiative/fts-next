package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TCACohortSelectorFactoryIT {

  @Autowired MeterRegistry meterRegistry;
  @Autowired WebClientFactory clientFactory;

  private TCACohortSelectorFactory factory;

  @BeforeEach
  void setUp() {
    factory = new TCACohortSelectorFactory(clientFactory, meterRegistry);
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
                new TCACohortSelectorConfig(
                    new HttpClientConfig("http://dummy.example.com"), null, null, null, null)))
        .isNotNull();
  }
}
