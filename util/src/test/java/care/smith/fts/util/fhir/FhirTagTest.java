package care.smith.fts.util.fhir;

import static org.assertj.core.api.Assertions.*;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

public class FhirTagTest {

  private static final String SYSTEM = "http://smith.care/fhir/tags";
  private static final String CODE = "test-tag";
  private static final String DISPLAY = "Test Tag";

  @Test
  public void testNullBundle() {
    assertThatNullPointerException()
        .isThrownBy(() -> FhirTag.addTagToAllResources(null, SYSTEM, CODE, DISPLAY));
  }

  @Test
  public void testEmptyBundle() {
    var emptyBundle = new Bundle();
    var result = FhirTag.addTagToAllResources(emptyBundle, SYSTEM, CODE, DISPLAY);

    assertThat(result).isNotNull();
    assertThat(result.getMeta()).isNotNull();
    assertThat(result.getMeta().getTag())
        .hasSize(1)
        .first()
        .satisfies(
            tag -> {
              assertThat(tag.getSystem()).isEqualTo(SYSTEM);
              assertThat(tag.getCode()).isEqualTo(CODE);
              assertThat(tag.getDisplay()).isEqualTo(DISPLAY);
            });
  }

  @Test
  public void testBundleWithResources() {
    var bundle = new Bundle();

    // Add a patient
    var patient = new Patient();
    patient.setId("patient-1");
    var patientEntry = new Bundle.BundleEntryComponent();
    patientEntry.setResource(patient);
    bundle.addEntry(patientEntry);

    // Add an observation
    var observation = new Observation();
    observation.setId("observation-1");
    var observationEntry = new Bundle.BundleEntryComponent();
    observationEntry.setResource(observation);
    bundle.addEntry(observationEntry);

    // Add the tag to all resources
    var result = FhirTag.addTagToAllResources(bundle, SYSTEM, CODE, DISPLAY);

    // Verify bundle has the tag
    assertThat(result.getMeta()).isNotNull();
    assertThat(result.getMeta().getTag())
        .hasSize(1)
        .first()
        .satisfies(
            tag -> {
              assertThat(tag.getSystem()).isEqualTo(SYSTEM);
              assertThat(tag.getCode()).isEqualTo(CODE);
              assertThat(tag.getDisplay()).isEqualTo(DISPLAY);
            });

    // Verify patient has the tag
    var resultPatient = (Patient) result.getEntry().getFirst().getResource();
    assertThat(resultPatient.getMeta()).isNotNull();
    assertThat(resultPatient.getMeta().getTag())
        .hasSize(1)
        .first()
        .satisfies(
            tag -> {
              assertThat(tag.getSystem()).isEqualTo(SYSTEM);
              assertThat(tag.getCode()).isEqualTo(CODE);
              assertThat(tag.getDisplay()).isEqualTo(DISPLAY);
            });

    // Verify observation has the tag
    var resultObservation = (Observation) result.getEntry().get(1).getResource();
    assertThat(resultObservation.getMeta()).isNotNull();
    assertThat(resultObservation.getMeta().getTag())
        .hasSize(1)
        .first()
        .satisfies(
            tag -> {
              assertThat(tag.getSystem()).isEqualTo(SYSTEM);
              assertThat(tag.getCode()).isEqualTo(CODE);
              assertThat(tag.getDisplay()).isEqualTo(DISPLAY);
            });
  }

  @Test
  public void testResourceWithExistingMeta() {
    var bundle = new Bundle();

    // Add a patient with existing meta and tag
    var patient = new Patient();
    patient.setId("patient-1");
    var existingMeta = new Meta();
    existingMeta.addTag().setSystem("http://existing.system").setCode("existing-code");
    patient.setMeta(existingMeta);

    var patientEntry = new Bundle.BundleEntryComponent();
    patientEntry.setResource(patient);
    bundle.addEntry(patientEntry);

    // Add the new tag to all resources
    var result = FhirTag.addTagToAllResources(bundle, SYSTEM, CODE, DISPLAY);

    // Verify patient now has both tags
    var resultPatient = (Patient) result.getEntry().getFirst().getResource();
    assertThat(resultPatient.getMeta()).isNotNull();
    assertThat(resultPatient.getMeta().getTag()).hasSize(2);

    // Verify the existing tag is preserved
    assertThat(resultPatient.getMeta().getTag())
        .filteredOn(tag -> "http://existing.system".equals(tag.getSystem()))
        .hasSize(1)
        .first()
        .satisfies(tag -> assertThat(tag.getCode()).isEqualTo("existing-code"));

    // Verify the new tag was added
    assertThat(resultPatient.getMeta().getTag())
        .filteredOn(tag -> SYSTEM.equals(tag.getSystem()))
        .hasSize(1)
        .first()
        .satisfies(
            tag -> {
              assertThat(tag.getCode()).isEqualTo(CODE);
              assertThat(tag.getDisplay()).isEqualTo(DISPLAY);
            });
  }

  @Test
  public void testWithoutDisplayText() {
    var bundle = new Bundle();
    var result = FhirTag.addTagToAllResources(bundle, SYSTEM, CODE, null);

    assertThat(result.getMeta().getTag())
        .hasSize(1)
        .first()
        .satisfies(
            tag -> {
              assertThat(tag.getSystem()).isEqualTo(SYSTEM);
              assertThat(tag.getCode()).isEqualTo(CODE);
              assertThat(tag.hasDisplay()).isFalse();
            });
  }

  @Test
  public void testWithEmptyDisplayText() {
    var bundle = new Bundle();
    var result = FhirTag.addTagToAllResources(bundle, SYSTEM, CODE, "");

    assertThat(result.getMeta().getTag())
        .hasSize(1)
        .first()
        .satisfies(
            tag -> {
              assertThat(tag.getSystem()).isEqualTo(SYSTEM);
              assertThat(tag.getCode()).isEqualTo(CODE);
              assertThat(tag.hasDisplay()).isFalse();
            });
  }

  @Test
  public void testEntryWithNullResource() {
    var bundle = new Bundle();

    // Add an entry with a null resource
    var emptyEntry = new Bundle.BundleEntryComponent();
    bundle.addEntry(emptyEntry);

    // Add a valid resource
    var patient = new Patient();
    var patientEntry = new Bundle.BundleEntryComponent();
    patientEntry.setResource(patient);
    bundle.addEntry(patientEntry);

    // Should not throw exception for null resource
    var result = FhirTag.addTagToAllResources(bundle, SYSTEM, CODE, DISPLAY);

    // Verify bundle has tag
    assertThat(result.getMeta().getTag()).hasSize(1);

    // Verify patient has tag
    var resultPatient = (Patient) result.getEntry().get(1).getResource();
    assertThat(resultPatient.getMeta().getTag()).hasSize(1);
  }
}
