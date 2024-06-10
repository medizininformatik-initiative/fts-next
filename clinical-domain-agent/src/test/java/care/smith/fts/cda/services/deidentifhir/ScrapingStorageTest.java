package care.smith.fts.cda.services.deidentifhir;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ScrapingStorageTest {
  ScrapingStorage scrapingStorage;

  @BeforeEach
  void setUp() {
    scrapingStorage = new ScrapingStorage(new NamespacingService("test"));
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
