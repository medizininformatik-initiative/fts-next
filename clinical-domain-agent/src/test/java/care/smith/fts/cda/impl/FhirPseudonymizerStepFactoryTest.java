package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.tca.TcaDomains;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
class FhirPseudonymizerStepFactoryTest {

  @Mock private WebClientFactory clientFactory;
  @Mock private WebClient webClient;
  @TempDir File tempDir;

  private FhirPseudonymizerStepFactory factory;

  @BeforeEach
  void setUp() {
    factory = new FhirPseudonymizerStepFactory(clientFactory, new SimpleMeterRegistry());
  }

  @Test
  void getConfigTypeReturnsFhirPseudonymizerConfig() {
    assertThat(factory.getConfigType()).isEqualTo(FhirPseudonymizerConfig.class);
  }

  @Test
  void createReturnsDeidentificatorWithValidConfig() throws IOException {
    when(clientFactory.create(any(HttpClientConfig.class))).thenReturn(webClient);

    var configFile = writeAnonymizationConfig();
    var serviceUrl = new HttpClientConfig("http://localhost:1234");
    var tcaServer = new HttpClientConfig("http://localhost:5678");
    var domains = new TcaDomains("pseudo", "salt", "dateshift");
    var tcaConfig = new FhirPseudonymizerConfig.TCAConfig(tcaServer, domains);

    var implConfig =
        new FhirPseudonymizerConfig(
            serviceUrl, configFile, tcaConfig, Duration.ofDays(14), DateShiftPreserve.NONE);

    Deidentificator result = factory.create(new Deidentificator.Config(), implConfig);

    assertThat(result).isInstanceOf(FhirPseudonymizerStep.class);
  }

  @Test
  void createThrowsWhenAnonymizationConfigIsNull() {
    when(clientFactory.create(any(HttpClientConfig.class))).thenReturn(webClient);

    var serviceUrl = new HttpClientConfig("http://localhost:1234");
    var tcaServer = new HttpClientConfig("http://localhost:5678");
    var domains = new TcaDomains("pseudo", "salt", "dateshift");
    var tcaConfig = new FhirPseudonymizerConfig.TCAConfig(tcaServer, domains);

    var implConfig =
        new FhirPseudonymizerConfig(
            serviceUrl, null, tcaConfig, Duration.ofDays(14), DateShiftPreserve.NONE);

    assertThatThrownBy(() -> factory.create(new Deidentificator.Config(), implConfig))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createThrowsWhenConfigFileDoesNotExist() {
    when(clientFactory.create(any(HttpClientConfig.class))).thenReturn(webClient);

    var nonExistent = new File(tempDir, "nonexistent.yaml");
    var serviceUrl = new HttpClientConfig("http://localhost:1234");
    var tcaServer = new HttpClientConfig("http://localhost:5678");
    var domains = new TcaDomains("pseudo", "salt", "dateshift");
    var tcaConfig = new FhirPseudonymizerConfig.TCAConfig(tcaServer, domains);

    var implConfig =
        new FhirPseudonymizerConfig(
            serviceUrl, nonExistent, tcaConfig, Duration.ofDays(14), DateShiftPreserve.NONE);

    assertThatThrownBy(() -> factory.create(new Deidentificator.Config(), implConfig))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to parse anonymization config");
  }

  private File writeAnonymizationConfig() throws IOException {
    var file = new File(tempDir, "anonymization.yaml");
    Files.writeString(
        file.toPath(),
        """
        fhirPathRules:
          - path: "Encounter.period.start"
            method: "dateshift"
        """);
    return file;
  }
}
