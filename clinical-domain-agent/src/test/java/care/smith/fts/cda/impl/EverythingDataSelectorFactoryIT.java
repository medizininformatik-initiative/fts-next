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
class EverythingDataSelectorFactoryIT {

  private static EverythingDataSelectorFactory factory;

  @BeforeEach
  void setUp(@Autowired MeterRegistry meterRegistry, @Autowired WebClientFactory clientFactory) {
    factory = new EverythingDataSelectorFactory(clientFactory, meterRegistry);
  }

  @Test
  void testConfigType() {
    assertThat(factory.getConfigType()).isNotNull();
  }

  @Test
  void testCreate() {
    var dataSelector =
        factory.create(
            null, new EverythingDataSelectorConfig(new HttpClientConfig("http://localhost"), 500));
    assertThat(dataSelector).isNotNull();
  }
}
