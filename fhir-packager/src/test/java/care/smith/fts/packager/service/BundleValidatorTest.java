package care.smith.fts.packager.service;

import care.smith.fts.packager.service.BundleValidator.BundleValidationException;
import care.smith.fts.packager.service.BundleValidator.ValidationMode;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for BundleValidator service.
 * 
 * <p>This test class does not require Spring Boot context since BundleValidator
 * is a standalone service that only depends on HAPI FHIR libraries.
 */
class BundleValidatorTest {

  private BundleValidator bundleValidator;

  @BeforeEach
  void setUp() {
    bundleValidator = new BundleValidator();
  }

  @Test
  void validateBundle_WithValidCollectionBundle_ShouldPass() throws Exception {
    Bundle bundle = createValidCollectionBundle();

    bundleValidator.validateBundle(bundle, ValidationMode.STRICT);
  }

  @Test
  void validateBundle_WithValidTransactionBundle_ShouldPass() throws Exception {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.TRANSACTION);
    bundle.addEntry(new Bundle.BundleEntryComponent().setResource(createPatient()));

    bundleValidator.validateBundle(bundle, ValidationMode.STRICT);
  }

  @Test
  void validateBundle_WithNullBundle_ShouldThrowException() {
    assertThatThrownBy(() -> bundleValidator.validateBundle(null, ValidationMode.STRICT))
        .isInstanceOf(BundleValidationException.class)
        .hasMessage("Bundle cannot be null");
  }

  @Test
  void validateBundle_WithNullType_StrictMode_ShouldThrowException() {
    Bundle bundle = new Bundle();
    bundle.setType(null);
    bundle.addEntry(new Bundle.BundleEntryComponent().setResource(createPatient()));

    assertThatThrownBy(() -> bundleValidator.validateBundle(bundle, ValidationMode.STRICT))
        .isInstanceOf(BundleValidationException.class)
        .hasMessage("Bundle type is required but was null");
  }

  @Test
  void validateBundle_WithNullType_LenientMode_ShouldPass() throws Exception {
    Bundle bundle = new Bundle();
    bundle.setType(null);
    bundle.addEntry(new Bundle.BundleEntryComponent().setResource(createPatient()));

    bundleValidator.validateBundle(bundle, ValidationMode.LENIENT);
  }

  @Test
  void validateBundle_WithEmptyEntries_StrictMode_ShouldThrowException() {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.COLLECTION);

    assertThatThrownBy(() -> bundleValidator.validateBundle(bundle, ValidationMode.STRICT))
        .isInstanceOf(BundleValidationException.class)
        .hasMessage("Bundle contains no entries");
  }

  @Test
  void validateBundle_WithEmptyEntries_LenientMode_ShouldPass() throws Exception {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.COLLECTION);

    bundleValidator.validateBundle(bundle, ValidationMode.LENIENT);
  }

  @Test
  void validateBundle_WithNullResourceInEntry_StrictMode_ShouldThrowException() {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.COLLECTION);
    bundle.addEntry(new Bundle.BundleEntryComponent()); // No resource

    assertThatThrownBy(() -> bundleValidator.validateBundle(bundle, ValidationMode.STRICT))
        .isInstanceOf(BundleValidationException.class)
        .hasMessage("Entry 1 contains no resource");
  }

  @Test
  void validateBundle_WithNullResourceInEntry_LenientMode_ShouldPass() throws Exception {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.COLLECTION);
    bundle.addEntry(new Bundle.BundleEntryComponent()); // No resource

    bundleValidator.validateBundle(bundle, ValidationMode.LENIENT);
  }

  @Test
  void validateBundle_WithAllNullResources_StrictMode_ShouldThrowException() {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.COLLECTION);
    bundle.addEntry(new Bundle.BundleEntryComponent());
    bundle.addEntry(new Bundle.BundleEntryComponent());

    assertThatThrownBy(() -> bundleValidator.validateBundle(bundle, ValidationMode.STRICT))
        .isInstanceOf(BundleValidationException.class)
        .hasMessage("Bundle contains no valid resources");
  }

  @Test
  void validateAndParseBundle_WithValidJson_ShouldReturnBundle() throws Exception {
    String validJson = loadTestResource("test-bundles/valid-collection-bundle.json");

    Bundle result = bundleValidator.validateAndParseBundle(validJson, ValidationMode.STRICT);

    assertThat(result).isNotNull();
    assertThat(result.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
    assertThat(result.getEntry()).hasSize(2);
  }

  @Test
  void validateAndParseBundle_WithInvalidJson_ShouldThrowException() {
    String invalidJson = "{ invalid json }";

    assertThatThrownBy(() -> bundleValidator.validateAndParseBundle(invalidJson, ValidationMode.STRICT))
        .isInstanceOf(BundleValidationException.class)
        .hasMessageContaining("Failed to parse bundle as valid FHIR JSON");
  }

  @Test
  void validateAndParseBundle_WithNullInput_ShouldThrowException() {
    assertThatThrownBy(() -> bundleValidator.validateAndParseBundle(null, ValidationMode.STRICT))
        .isInstanceOf(BundleValidationException.class)
        .hasMessage("Bundle string is null or empty");
  }

  @Test
  void validateAndParseBundle_WithEmptyInput_ShouldThrowException() {
    assertThatThrownBy(() -> bundleValidator.validateAndParseBundle("", ValidationMode.STRICT))
        .isInstanceOf(BundleValidationException.class)
        .hasMessage("Bundle string is null or empty");
  }

  @Test
  void validateAndParseBundle_WithWhitespaceOnly_ShouldThrowException() {
    assertThatThrownBy(() -> bundleValidator.validateAndParseBundle("   \t\n  ", ValidationMode.STRICT))
        .isInstanceOf(BundleValidationException.class)
        .hasMessage("Bundle string is null or empty");
  }

  @Test
  void validateAndParseBundle_WithNonBundleResource_ShouldThrowException() throws Exception {
    String patientJson = loadTestResource("test-bundles/not-a-bundle.json");

    assertThatThrownBy(() -> bundleValidator.validateAndParseBundle(patientJson, ValidationMode.STRICT))
        .isInstanceOf(BundleValidationException.class)
        .hasMessageContaining("Failed to parse bundle as valid FHIR JSON");
  }

  @Test
  void validateAndParseBundle_WithBundleNoType_LenientMode_ShouldPass() throws Exception {
    String bundleJson = loadTestResource("test-bundles/bundle-no-type.json");

    Bundle result = bundleValidator.validateAndParseBundle(bundleJson, ValidationMode.LENIENT);

    assertThat(result).isNotNull();
    assertThat(result.getType()).isNull();
    assertThat(result.getEntry()).hasSize(1);
  }

  @Test
  void validateAndParseBundle_WithEmptyBundle_StrictMode_ShouldThrowException() throws Exception {
    String emptyBundleJson = loadTestResource("test-bundles/empty-bundle.json");

    assertThatThrownBy(() -> bundleValidator.validateAndParseBundle(emptyBundleJson, ValidationMode.STRICT))
        .isInstanceOf(BundleValidationException.class)
        .hasMessage("Bundle contains no entries");
  }

  @Test
  void validateAndParseBundle_WithEmptyBundle_LenientMode_ShouldPass() throws Exception {
    String emptyBundleJson = loadTestResource("test-bundles/empty-bundle.json");

    Bundle result = bundleValidator.validateAndParseBundle(emptyBundleJson, ValidationMode.LENIENT);

    assertThat(result).isNotNull();
    assertThat(result.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
    assertThat(result.getEntry()).isEmpty();
  }

  private Bundle createValidCollectionBundle() {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.COLLECTION);
    bundle.addEntry(new Bundle.BundleEntryComponent().setResource(createPatient()));
    bundle.addEntry(new Bundle.BundleEntryComponent().setResource(createObservation()));
    return bundle;
  }

  private Patient createPatient() {
    Patient patient = new Patient();
    patient.setId("patient-1");
    patient.setActive(true);
    patient.addName(new org.hl7.fhir.r4.model.HumanName()
        .setFamily("Doe")
        .addGiven("John"));
    return patient;
  }

  private Observation createObservation() {
    Observation observation = new Observation();
    observation.setId("observation-1");
    observation.setStatus(Observation.ObservationStatus.FINAL);
    return observation;
  }

  private String loadTestResource(String resourcePath) throws IOException {
    ClassPathResource resource = new ClassPathResource(resourcePath);
    return resource.getContentAsString(StandardCharsets.UTF_8);
  }
}