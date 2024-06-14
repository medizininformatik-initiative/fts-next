package care.smith.fts.cda.services;

import static org.assertj.core.api.Assertions.*;

import ca.uhn.fhir.context.FhirContext;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class FhirResolveConfigTest {

  private static final FhirContext FHIR = FhirContext.forR4();
  private static final WebClient CLIENT =
      WebClient.builder().baseUrl("https://some.example.com").build();

  @Test
  void nullSystemThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new FhirResolveConfig(null).createService(CLIENT));
  }

  @Test
  void emptySystemThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new FhirResolveConfig(""));
  }

  @Test
  void createThrowsOnEmptyClient() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new FhirResolveConfig("https://some.exampl.com").createService(null));
  }

  @Test
  void createSucceeds() {
    assertThat(new FhirResolveConfig("https://some.example.com").createService(CLIENT)).isNotNull();
  }
}
