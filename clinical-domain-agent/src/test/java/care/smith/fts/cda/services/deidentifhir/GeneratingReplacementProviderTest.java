package care.smith.fts.cda.services.deidentifhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeneratingReplacementProviderTest {
  private GeneratingReplacementProvider provider;

  @BeforeEach
  void setUp() {
    provider = new GeneratingReplacementProvider("patient-123");
  }

  @Test
  void nullPatientIdentifierThrowsNpe() {
    assertThatNullPointerException().isThrownBy(() -> new GeneratingReplacementProvider(null));
  }

  @Test
  void sameIdReturnsSameTid() {
    var tId1 = provider.getIDReplacement("Patient", "id-1");
    var tId2 = provider.getIDReplacement("Patient", "id-1");

    assertThat(tId1).isEqualTo(tId2);
  }

  @Test
  void differentIdsReturnDifferentTids() {
    var tId1 = provider.getIDReplacement("Patient", "id-1");
    var tId2 = provider.getIDReplacement("Patient", "id-2");

    assertThat(tId1).isNotEqualTo(tId2);
  }

  @Test
  void namespacingAppliedToOriginalIds() {
    provider.getIDReplacement("Patient", "id-1");

    var idMappings = provider.getIdMappings();

    assertThat(idMappings).containsKey("patient-123.Patient:id-1");
  }

  @Test
  void identifierValueReplacementUsesNamespacedKey() {
    provider.getValueReplacement("http://fhir.de/sid/gkv/kvid-10", "A123456789");

    var idMappings = provider.getIdMappings();

    assertThat(idMappings)
        .containsKey("patient-123.identifier.http://fhir.de/sid/gkv/kvid-10:A123456789");
  }

  @Test
  void sameIdentifierValueReturnsSameTid() {
    var tId1 = provider.getValueReplacement("system", "value");
    var tId2 = provider.getValueReplacement("system", "value");

    assertThat(tId1).isEqualTo(tId2);
  }

  @Test
  void differentIdentifierValuesReturnDifferentTids() {
    var tId1 = provider.getValueReplacement("system", "value-1");
    var tId2 = provider.getValueReplacement("system", "value-2");

    assertThat(tId1).isNotEqualTo(tId2);
  }

  @Test
  void getIdMappingsReturnsAllMappings() {
    provider.getIDReplacement("Patient", "p1");
    provider.getIDReplacement("Observation", "o1");
    provider.getValueReplacement("system", "val");

    var idMappings = provider.getIdMappings();

    assertThat(idMappings).hasSize(3);
    assertThat(idMappings)
        .containsKeys(
            "patient-123.Patient:p1",
            "patient-123.Observation:o1",
            "patient-123.identifier.system:val");
  }

  @Test
  void generatedTidsHaveExpectedLength() {
    var tId = provider.getIDReplacement("Patient", "id-1");

    assertThat(tId).hasSize(21);
  }

  @Test
  void generateDateTidCreatesUniqueTid() {
    var tId1 = provider.generateDateTid("2024-01-15");
    var tId2 = provider.generateDateTid("2024-01-16");

    assertThat(tId1).isNotEqualTo(tId2);
    assertThat(tId1).hasSize(21);
    assertThat(tId2).hasSize(21);
  }

  @Test
  void generateDateTidDeduplicatesSameDate() {
    var tId1 = provider.generateDateTid("2024-01-15");
    var tId2 = provider.generateDateTid("2024-01-15");

    assertThat(tId1).isEqualTo(tId2);
  }

  @Test
  void getDateMappingsReturnsAllDateMappings() {
    var tId1 = provider.generateDateTid("2024-01-15");
    var tId2 = provider.generateDateTid("2024-01-16");

    var dateMappings = provider.getDateMappings();

    assertThat(dateMappings).hasSize(2);
    assertThat(dateMappings).containsEntry(tId1, "2024-01-15");
    assertThat(dateMappings).containsEntry(tId2, "2024-01-16");
  }

  @Test
  void idMappingsAreImmutable() {
    provider.getIDReplacement("Patient", "id-1");

    var idMappings = provider.getIdMappings();
    provider.getIDReplacement("Patient", "id-2");

    assertThat(idMappings).hasSize(1);
  }

  @Test
  void dateMappingsAreImmutable() {
    provider.generateDateTid("2024-01-15");

    var dateMappings = provider.getDateMappings();
    provider.generateDateTid("2024-01-16");

    assertThat(dateMappings).hasSize(1);
  }
}
