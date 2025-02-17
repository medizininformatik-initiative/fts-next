package care.smith.fts.cda.services;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class FhirResolveConfigTest {

  MeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

  private static final WebClient CLIENT =
      WebClient.builder().baseUrl("https://some.example.com").build();

  @Test
  void nullSystemThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new FhirResolveConfig(null).createService(CLIENT, meterRegistry));
  }

  @Test
  void emptySystemThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new FhirResolveConfig(""));
  }

  @Test
  void createThrowsOnEmptyClient() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(
            () -> new FhirResolveConfig("https://some.exampl.com").createService(null, null));
  }

  @Test
  void createSucceeds() {
    assertThat(
            new FhirResolveConfig("https://some.example.com").createService(CLIENT, meterRegistry))
        .isNotNull();
  }
}
