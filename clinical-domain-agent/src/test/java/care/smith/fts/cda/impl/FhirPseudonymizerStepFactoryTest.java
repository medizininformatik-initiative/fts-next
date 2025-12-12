package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
class FhirPseudonymizerStepFactoryTest {

  @Mock private WebClientFactory clientFactory;
  @Mock private WebClient webClient;

  private MeterRegistry meterRegistry;
  private FhirContext fhirContext;
  private FhirPseudonymizerStepFactory factory;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    fhirContext = FhirContext.forR4();
    factory = new FhirPseudonymizerStepFactory(clientFactory, meterRegistry, fhirContext);
  }

  @Test
  void getConfigTypeReturnsFhirPseudonymizerConfigClass() {
    assertThat(factory.getConfigType()).isEqualTo(FhirPseudonymizerConfig.class);
  }

  @Test
  void createReturnsDeidentificator() {
    var server = new HttpClientConfig("http://fhir-pseudonymizer:8080");
    var implConfig = new FhirPseudonymizerConfig(server, Duration.ofSeconds(30), 3);
    var commonConfig = new Deidentificator.Config();

    when(clientFactory.create(any(HttpClientConfig.class))).thenReturn(webClient);

    var result = factory.create(commonConfig, implConfig);

    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(FhirPseudonymizerStep.class);
  }

  @Test
  void createWithDefaultConfigValues() {
    var server = new HttpClientConfig("http://fhir-pseudonymizer:8080");
    var implConfig = new FhirPseudonymizerConfig(server, null, null);
    var commonConfig = new Deidentificator.Config();

    when(clientFactory.create(any(HttpClientConfig.class))).thenReturn(webClient);

    var result = factory.create(commonConfig, implConfig);

    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(FhirPseudonymizerStep.class);
  }
}
