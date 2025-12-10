package care.smith.fts.rda.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.util.HttpClientConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FhirPseudonymizerConfigTest {

  @Test
  void createWithAllParametersExplicit() {
    var server = new HttpClientConfig("http://fhir-pseudonymizer:8080");
    var timeout = Duration.ofSeconds(30);
    var maxRetries = 5;

    var config = new FhirPseudonymizerConfig(server, timeout, maxRetries);

    assertThat(config.server()).isEqualTo(server);
    assertThat(config.timeout()).isEqualTo(timeout);
    assertThat(config.maxRetries()).isEqualTo(5);
  }

  @Test
  void createWithDefaultTimeout() {
    var server = new HttpClientConfig("http://fhir-pseudonymizer:8080");

    var config = new FhirPseudonymizerConfig(server, null, 5);

    assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(60));
  }

  @Test
  void createWithDefaultMaxRetries() {
    var server = new HttpClientConfig("http://fhir-pseudonymizer:8080");

    var config = new FhirPseudonymizerConfig(server, Duration.ofSeconds(30), null);

    assertThat(config.maxRetries()).isEqualTo(3);
  }

  @Test
  void createWithAllDefaults() {
    var server = new HttpClientConfig("http://fhir-pseudonymizer:8080");

    var config = new FhirPseudonymizerConfig(server, null, null);

    assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(60));
    assertThat(config.maxRetries()).isEqualTo(3);
  }

  @Test
  void negativeTimeoutThrowsException() {
    var server = new HttpClientConfig("http://fhir-pseudonymizer:8080");

    assertThatThrownBy(() -> new FhirPseudonymizerConfig(server, Duration.ofSeconds(-1), 3))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Timeout must be positive");
  }

  @Test
  void zeroTimeoutThrowsException() {
    var server = new HttpClientConfig("http://fhir-pseudonymizer:8080");

    assertThatThrownBy(() -> new FhirPseudonymizerConfig(server, Duration.ZERO, 3))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Timeout must be positive");
  }

  @Test
  void negativeMaxRetriesThrowsException() {
    var server = new HttpClientConfig("http://fhir-pseudonymizer:8080");

    assertThatThrownBy(() -> new FhirPseudonymizerConfig(server, Duration.ofSeconds(30), -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Max retries must be non-negative");
  }

  @Test
  void zeroMaxRetriesIsAllowed() {
    var server = new HttpClientConfig("http://fhir-pseudonymizer:8080");

    var config = new FhirPseudonymizerConfig(server, Duration.ofSeconds(30), 0);

    assertThat(config.maxRetries()).isZero();
  }
}
