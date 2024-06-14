package care.smith.fts.cda.services.deidentifhir;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.deidentifhir.NamespacingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScrapingStorageTest {
  ScrapingStorage scrapingStorage;

  @BeforeEach
  void setUp() {
    scrapingStorage = new ScrapingStorage(NamespacingService.withNamespacing("test"));
  }

  @Test
  void getIDReplacement() {
    assertThat(scrapingStorage.getIDReplacement("Patient", "patientId")).isEqualTo("patientId");
    assertThat(scrapingStorage.getGatheredIDATs()).containsExactly("test.id.Patient:patientId");
  }

  @Test
  void getValueReplacement() {
    assertThat(scrapingStorage.getValueReplacement("Patient", "patientId")).isEqualTo("patientId");
    assertThat(scrapingStorage.getGatheredIDATs())
        .containsExactly("test.identifier.Patient:patientId");
  }
}
