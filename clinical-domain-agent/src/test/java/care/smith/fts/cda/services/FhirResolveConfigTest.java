package care.smith.fts.cda.services;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FhirResolveConfigTest {

  private static final FhirContext FHIR = FhirContext.forR4();
  private static final IGenericClient CLIENT =
      FHIR.newRestfulGenericClient("https://some.example.com");

  @Test
  void nullSystemThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new FhirResolveConfig(null).createService(CLIENT, FHIR));
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
    assertThat(new FhirResolveConfig("https://some.example.com").createService(CLIENT, FHIR))
        .isNotNull();
  }
}