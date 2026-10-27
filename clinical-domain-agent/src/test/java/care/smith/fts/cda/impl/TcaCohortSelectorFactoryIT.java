package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TcaCohortSelectorFactoryIT {

  @Autowired MeterRegistry meterRegistry;
  @Autowired WebClientFactory clientFactory;

  private TcaCohortSelectorFactory factory;

  @BeforeEach
  void setUp() {
    factory = new TcaCohortSelectorFactory(clientFactory, new DefaultRetryStrategy(meterRegistry));
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

  private static TcaCohortSelectorConfig config(Set<String> policies) {
    return new TcaCohortSelectorConfig(
        new HttpClientConfig("http://dummy.example.com"),
        "http://example.org/fhir/patient-id",
        "http://example.org/fhir/policy",
        policies,
        "MII",
        null);
  }
}
