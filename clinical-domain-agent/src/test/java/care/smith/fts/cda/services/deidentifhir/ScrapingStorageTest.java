package care.smith.fts.cda.services.deidentifhir;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScrapingStorageTest {
  ScrapingStorage scrapingStorage;

  @BeforeEach
  void setUp() {
    scrapingStorage = new ScrapingStorage(NamespacingReplacementProvider.withNamespacing("test"));
  }

  @Test
  void getIDReplacement() {
    assertThat(scrapingStorage.getIDReplacement("Patient", "patientId")).isEqualTo("patientId");
    assertThat(scrapingStorage.getGatheredIDATs()).containsExactly("test.Patient:patientId");
  }

  @Test
  void getValueReplacement() {
    assertThat(scrapingStorage.getValueReplacement("Patient", "patientId")).isEqualTo("patientId");
    assertThat(scrapingStorage.getGatheredIDATs())
        .containsExactly("test.identifier.Patient:patientId");
  }
}
