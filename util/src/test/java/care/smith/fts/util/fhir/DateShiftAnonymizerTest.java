package care.smith.fts.util.fhir;

import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_EXTENSION_URL;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DateShiftAnonymizerTest {

  @TempDir File tempDir;

  @Test
  void parseDateShiftPathsExtractsOnlyDateshiftRules() throws IOException {
    var config =
        """
        fhirPathRules:
          - path: "Encounter.period.start"
            method: "dateshift"
          - path: "Encounter.period.end"
            method: "dateshift"
          - path: "Patient.identifier.value"
            method: "pseudonymize"
          - path: "Patient.birthDate"
            method: "dateshift"
        """;
    var file = writeConfig(config);

    var paths = DateShiftAnonymizer.parseDateShiftPaths(file);

    assertThat(paths)
        .containsExactlyInAnyOrder(
            "Encounter.period.start", "Encounter.period.end", "Patient.birthDate");
  }

  @Test
  void parseDateShiftPathsReturnsEmptyForNoDateshiftRules() throws IOException {
    var config =
        """
        fhirPathRules:
          - path: "Patient.identifier.value"
            method: "pseudonymize"
        """;
    var file = writeConfig(config);

    var paths = DateShiftAnonymizer.parseDateShiftPaths(file);

    assertThat(paths).isEmpty();
  }

  @Test
  void parseDateShiftPathsReturnsEmptyForMissingRules() throws IOException {
    var config = "someOtherConfig: true\n";
    var file = writeConfig(config);

    var paths = DateShiftAnonymizer.parseDateShiftPaths(file);

    assertThat(paths).isEmpty();
  }

  @Test
  void nullifyDatesNullifiesMatchingDateElements() {
    var encounter = new Encounter();
    var period = new Period();
    period.setStartElement(new DateTimeType("2020-06-15T10:30:00+02:00"));
    period.setEndElement(new DateTimeType("2020-06-16T08:00:00+02:00"));
    encounter.setPeriod(period);

    var bundle = new Bundle();
    bundle.addEntry().setResource(encounter);

    var paths = List.of("Encounter.period.start", "Encounter.period.end");

    var dateMappings = DateShiftAnonymizer.nullifyDates(bundle, paths);

    assertThat(dateMappings).hasSize(2);
    assertThat(period.getStartElement().getValue()).isNull();
    assertThat(period.getEndElement().getValue()).isNull();
    assertThat(period.getStartElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNotNull();
    assertThat(period.getEndElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNotNull();
  }

  @Test
  void nullifyDatesDeduplicatesSameDateValues() {
    var enc1 = new Encounter();
    var period1 = new Period();
    period1.setStartElement(new DateTimeType("2020-06-15"));
    enc1.setPeriod(period1);

    var enc2 = new Encounter();
    var period2 = new Period();
    period2.setStartElement(new DateTimeType("2020-06-15"));
    enc2.setPeriod(period2);

    var bundle = new Bundle();
    bundle.addEntry().setResource(enc1);
    bundle.addEntry().setResource(enc2);

    var paths = List.of("Encounter.period.start");

    var dateMappings = DateShiftAnonymizer.nullifyDates(bundle, paths);

    // Same date value → same tID, so only 1 entry in dateMappings
    assertThat(dateMappings).hasSize(1);

    // Both elements should have the same tID in their extension
    var ext1 = period1.getStartElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL);
    var ext2 = period2.getStartElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL);
    assertThat(ext1.getValue().primitiveValue()).isEqualTo(ext2.getValue().primitiveValue());
  }

  @Test
  void nullifyDatesSkipsNonMatchingResourceTypes() {
    var patient = new Patient();
    patient.setBirthDate(new Date());

    var encounter = new Encounter();
    var period = new Period();
    period.setStartElement(new DateTimeType("2020-06-15"));
    encounter.setPeriod(period);

    var bundle = new Bundle();
    bundle.addEntry().setResource(patient);
    bundle.addEntry().setResource(encounter);

    // Only target Encounter dates, not Patient
    var paths = List.of("Encounter.period.start");

    var dateMappings = DateShiftAnonymizer.nullifyDates(bundle, paths);

    assertThat(dateMappings).hasSize(1);
    // Patient birthDate should be unchanged
    assertThat(patient.getBirthDate()).isNotNull();
  }

  @Test
  void nullifyDatesWithEmptyPathsReturnsEmpty() {
    var bundle = new Bundle();
    bundle.addEntry().setResource(new Encounter());

    var dateMappings = DateShiftAnonymizer.nullifyDates(bundle, List.of());

    assertThat(dateMappings).isEmpty();
  }

  @Test
  void nullifyDatesSkipsNullDateValues() {
    var encounter = new Encounter();
    encounter.setPeriod(new Period()); // period with no start/end set

    var bundle = new Bundle();
    bundle.addEntry().setResource(encounter);

    var paths = List.of("Encounter.period.start");

    var dateMappings = DateShiftAnonymizer.nullifyDates(bundle, paths);

    assertThat(dateMappings).isEmpty();
  }

  @Test
  void nullifyDatesSkipsBaseDateTimeWithNullValue() {
    var encounter = new Encounter();
    var period = new Period();
    // Explicitly set a DateTimeType with null value — the element exists but has no value
    period.setStartElement(new DateTimeType((Date) null));
    encounter.setPeriod(period);

    var bundle = new Bundle();
    bundle.addEntry().setResource(encounter);

    var paths = List.of("Encounter.period.start");

    var dateMappings = DateShiftAnonymizer.nullifyDates(bundle, paths);

    assertThat(dateMappings).isEmpty();
  }

  @Test
  void parseDateShiftPathsReturnsEmptyForNullYamlRoot() throws IOException {
    var file = writeConfig(""); // empty YAML parses to null root
    var paths = DateShiftAnonymizer.parseDateShiftPaths(file);
    assertThat(paths).isEmpty();
  }

  @Test
  void parseDateShiftPathsFiltersNullAndEmptyPaths() throws IOException {
    var config =
        """
        fhirPathRules:
          - path: "Encounter.period.start"
            method: "dateshift"
          - method: "dateshift"
          - path: ""
            method: "dateshift"
        """;
    var file = writeConfig(config);

    var paths = DateShiftAnonymizer.parseDateShiftPaths(file);

    assertThat(paths).containsExactly("Encounter.period.start");
  }

  @Test
  void nullifyDatesSkipsNullResource() {
    var bundle = new Bundle();
    bundle.addEntry(); // entry with no resource (null)
    var encounter = new Encounter();
    var period = new Period();
    period.setStartElement(new DateTimeType("2020-06-15"));
    encounter.setPeriod(period);
    bundle.addEntry().setResource(encounter);

    var paths = List.of("Encounter.period.start");

    var dateMappings = DateShiftAnonymizer.nullifyDates(bundle, paths);

    assertThat(dateMappings).hasSize(1);
    assertThat(period.getStartElement().getValue()).isNull();
  }

  @Test
  void nullifyDatesSkipsNonDateTimeValues() {
    // Target a path that resolves to a non-BaseDateTimeType (e.g., StringType)
    var encounter = new Encounter();
    encounter.setId("enc-1");
    encounter.addIdentifier().setValue("some-identifier");

    var bundle = new Bundle();
    bundle.addEntry().setResource(encounter);

    // identifier.value resolves to a StringType, not BaseDateTimeType
    var paths = List.of("Encounter.identifier.value");

    var dateMappings = DateShiftAnonymizer.nullifyDates(bundle, paths);

    assertThat(dateMappings).isEmpty();
  }

  private File writeConfig(String content) throws IOException {
    var file = new File(tempDir, "anonymization.yaml");
    Files.writeString(file.toPath(), content);
    return file;
  }
}
