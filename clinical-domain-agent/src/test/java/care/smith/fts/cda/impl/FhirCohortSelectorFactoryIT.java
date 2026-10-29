package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import care.smith.fts.cda.TransferProcessRunnerConfig;
import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.scheduler.Schedulers;

@SpringBootTest
class FhirCohortSelectorFactoryIT {

  @Autowired MeterRegistry meterRegistry;
  @Autowired WebClientFactory clientFactory;
  @Autowired TransferProcessRunnerConfig runnerConfig;

  private FhirCohortSelectorFactory factory;

  @BeforeEach
  void setUp() {
    factory =
        new FhirCohortSelectorFactory(
            clientFactory,
            new DefaultRetryStrategy(meterRegistry),
            Schedulers.parallel(),
            runnerConfig);
  }

  @Test
  void testConfigType() {
    assertThat(factory.getConfigType()).isNotNull();
  }

  @Test
  void testCreate() {
    assertThat(factory.create(null, config(Set.of("1.2.3")))).isNotNull();
  }

  @Test
  void createWithoutPoliciesFails() {
    assertThatNullPointerException()
        .isThrownBy(() -> factory.create(null, config(null)))
        .withMessageContaining("policies");
  }

  private static FhirCohortSelectorConfig config(Set<String> policies) {
    return new FhirCohortSelectorConfig(
        new HttpClientConfig("http://dummy.example.com"),
        "http://example.org/fhir/patient-id",
        "http://example.org/fhir/policy",
        policies);
  }
}
