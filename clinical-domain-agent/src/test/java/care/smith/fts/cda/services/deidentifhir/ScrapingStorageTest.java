package care.smith.fts.cda.services.deidentifhir;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScrapingStorageTest {

  private static final String PATIENT_ID = "patient123";

  private ScrapingStorage storage;

  @BeforeEach
  void setUp() {
    var keyCreator = NamespacingReplacementProvider.withNamespacing(PATIENT_ID);
    storage = new ScrapingStorage(keyCreator);
  }

  @Test
  void getIDReplacement_inCompartment_hasPatientPrefix() {
    storage.setCompartmentMembership(Map.of("Observation:obs1", true));

    var result = storage.getIDReplacement("Observation", "obs1");

    assertThat(result).isEqualTo("obs1");
    assertThat(storage.getGatheredIdats()).contains("patient123.Observation:obs1");
  }

  @Test
  void getIDReplacement_notInCompartment_noPatientPrefix() {
    storage.setCompartmentMembership(Map.of("Organization:org1", false));

    var result = storage.getIDReplacement("Organization", "org1");

    assertThat(result).isEqualTo("org1");
    assertThat(storage.getGatheredIdats()).contains("Organization:org1");
    assertThat(storage.getGatheredIdats()).doesNotContain("patient123.Organization:org1");
  }

  @Test
  void getIDReplacement_unknownResource_defaultsToCompartment() {
    // If not in membership map, defaults to true (in compartment)
    var result = storage.getIDReplacement("Unknown", "unknown1");

    assertThat(result).isEqualTo("unknown1");
    assertThat(storage.getGatheredIdats()).contains("patient123.Unknown:unknown1");
  }

  @Test
  void getValueReplacement_alwaysHasPatientPrefix() {
    var result = storage.getValueReplacement("urn:system", "value123");

    assertThat(result).isEqualTo("value123");
    assertThat(storage.getGatheredIdats()).contains("patient123.identifier.urn:system:value123");
  }

  @Test
  void gatheredIdats_accumulatesAcrossMultipleCalls() {
    storage.setCompartmentMembership(
        Map.of(
            "Observation:obs1", true,
            "Organization:org1", false));

    storage.getIDReplacement("Observation", "obs1");
    storage.getIDReplacement("Organization", "org1");
    storage.getValueReplacement("system", "value1");

    assertThat(storage.getGatheredIdats())
        .containsExactlyInAnyOrder(
            "patient123.Observation:obs1",
            "Organization:org1",
            "patient123.identifier.system:value1");
  }
}
