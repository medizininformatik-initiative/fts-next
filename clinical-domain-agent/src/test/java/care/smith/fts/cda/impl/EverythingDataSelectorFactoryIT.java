package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.services.FhirResolveConfig;
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
  void testCreateWithoutResolver() {
    var dataSelector =
        factory.create(
            null,
            new EverythingDataSelectorConfig(new HttpClientConfig("http://localhost"), null, 500));
    assertThat(dataSelector).isNotNull();
  }

  @Test
  void testCreateWithResolver() {
    assertThat(
            factory.create(
                null,
                new EverythingDataSelectorConfig(
                    new HttpClientConfig("http://localhost"),
                    new FhirResolveConfig("https://patient-identifier.example.com"),
                    500)))
        .isNotNull();
  }
}
