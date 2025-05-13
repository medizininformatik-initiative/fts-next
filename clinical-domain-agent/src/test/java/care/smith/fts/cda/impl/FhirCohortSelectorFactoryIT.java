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
class FhirCohortSelectorFactoryIT {

  @Autowired MeterRegistry meterRegistry;
  @Autowired WebClientFactory clientFactory;

  private FhirCohortSelectorFactory factory;

  @BeforeEach
  void setUp() {
    factory = new FhirCohortSelectorFactory(clientFactory, meterRegistry);
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
                new FhirCohortSelectorConfig(
                    new HttpClientConfig("http://dummy.example.com"),
                    "http://example.org/fhir/patient-id",
                    "http://example.org/fhir/policy",
                    null)))
        .isNotNull();
  }
}
