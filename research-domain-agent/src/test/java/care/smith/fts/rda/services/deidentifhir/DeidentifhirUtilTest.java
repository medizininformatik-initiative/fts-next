package care.smith.fts.rda.services.deidentifhir;

import static care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil.generateRegistry;
import static care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil.restoreShiftedDates;
import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_EXTENSION_URL;
import static com.typesafe.config.ConfigFactory.parseResources;
import static org.assertj.core.api.Assertions.assertThat;

import de.ume.deidentifhir.Registry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

class DeidentifhirUtilTest {

  MeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

  @Test
  void deidentify() throws IOException {
    Map<String, String> transportIDs = Map.of("tid1", "pid1", "tidentifier1", "pidentifier1");

    Registry registry = generateRegistry(transportIDs);
    var config = parseResources(DeidentifhirUtilTest.class, "TransportToRD.profile");
    var transportBundle = generateOnePatient("tid1", "2023", "identifierSystem1", "tidentifier1");

    var pseudomizedBundle =
        DeidentifhirUtil.deidentify(config, registry, transportBundle, meterRegistry);

    Bundle b = (Bundle) pseudomizedBundle.getEntryFirstRep().getResource();
    Patient p = (Patient) b.getEntryFirstRep().getResource();

    assertThat(pseudomizedBundle.getEntry()).hasSize(1);
    assertThat(b.getEntry()).hasSize(1);
    assertThat(p.getId()).isEqualTo("Patient/pid1");
    assertThat(p.getIdentifierFirstRep().getValue()).isEqualTo("pidentifier1");
  }

  @Test
  void restoreShiftedDatesRestoresDateWithExtension() {
    var bundle = new Bundle();
    var patient = new Patient();
    var birthDate = new DateType("2000-01-01");
    birthDate.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("tId-birthDate"));
    patient.setBirthDateElement(birthDate);
    bundle.addEntry().setResource(patient);

    var dateShiftMap = Map.of("tId-birthDate", "2000-01-15");

    restoreShiftedDates(bundle, dateShiftMap);

    assertThat(patient.getBirthDateElement().getValueAsString()).isEqualTo("2000-01-15");
    assertThat(patient.getBirthDateElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNull();
  }

  @Test
  void restoreShiftedDatesHandlesNestedDateTimeElements() {
    var bundle = new Bundle();
    var observation = new Observation();
    var effectiveDateTime = new DateTimeType("2024-03-15T10:00:00Z");
    effectiveDateTime.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("tId-effective"));
    observation.setEffective(effectiveDateTime);
    bundle.addEntry().setResource(observation);

    var dateShiftMap = Map.of("tId-effective", "2024-03-20T10:00:00Z");

    restoreShiftedDates(bundle, dateShiftMap);

    var restored = (DateTimeType) observation.getEffective();
    assertThat(restored.getValueAsString()).isEqualTo("2024-03-20T10:00:00Z");
    assertThat(restored.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNull();
  }

  @Test
  void restoreShiftedDatesHandlesPeriodWithStartAndEnd() {
    var bundle = new Bundle();
    var observation = new Observation();
    var period = new Period();
    var start = new DateTimeType("2024-01-01T08:00:00Z");
    start.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("tId-start"));
    var end = new DateTimeType("2024-01-01T12:00:00Z");
    end.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("tId-end"));
    period.setStartElement(start);
    period.setEndElement(end);
    observation.setEffective(period);
    bundle.addEntry().setResource(observation);

    var dateShiftMap =
        Map.of("tId-start", "2024-01-05T08:00:00Z", "tId-end", "2024-01-05T12:00:00Z");

    restoreShiftedDates(bundle, dateShiftMap);

    var restoredPeriod = (Period) observation.getEffective();
    assertThat(restoredPeriod.getStartElement().getValueAsString())
        .isEqualTo("2024-01-05T08:00:00Z");
    assertThat(restoredPeriod.getEndElement().getValueAsString()).isEqualTo("2024-01-05T12:00:00Z");
    assertThat(restoredPeriod.getStartElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL))
        .isNull();
    assertThat(restoredPeriod.getEndElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNull();
  }

  @Test
  void restoreShiftedDatesRemovesExtensionEvenWhenMappingMissing() {
    var bundle = new Bundle();
    var patient = new Patient();
    var birthDate = new DateType("2000-01-01");
    birthDate.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("unknown-tId"));
    patient.setBirthDateElement(birthDate);
    bundle.addEntry().setResource(patient);

    var dateShiftMap = Map.<String, String>of();

    restoreShiftedDates(bundle, dateShiftMap);

    assertThat(patient.getBirthDateElement().getValueAsString()).isEqualTo("2000-01-01");
    assertThat(patient.getBirthDateElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNull();
  }

  @Test
  void restoreShiftedDatesIgnoresElementsWithoutExtension() {
    var bundle = new Bundle();
    var patient = new Patient();
    patient.setBirthDate(java.sql.Date.valueOf("2000-01-01"));
    bundle.addEntry().setResource(patient);

    var dateShiftMap = Map.of("some-tId", "2000-01-15");

    restoreShiftedDates(bundle, dateShiftMap);

    assertThat(patient.getBirthDateElement().getValueAsString()).isEqualTo("2000-01-01");
  }

  @Test
  void restoreShiftedDatesHandlesEmptyBundle() {
    var bundle = new Bundle();
    var dateShiftMap = Map.of("tId", "2024-01-01");

    restoreShiftedDates(bundle, dateShiftMap);

    assertThat(bundle.getEntry()).isEmpty();
  }

  @Test
  void restoreShiftedDatesHandlesMultipleResources() {
    var bundle = new Bundle();

    var patient = new Patient();
    var birthDate = new DateType("2000-01-01");
    birthDate.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("tId-patient"));
    patient.setBirthDateElement(birthDate);
    bundle.addEntry().setResource(patient);

    var observation = new Observation();
    var effectiveDateTime = new DateTimeType("2024-03-15T10:00:00Z");
    effectiveDateTime.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("tId-obs"));
    observation.setEffective(effectiveDateTime);
    bundle.addEntry().setResource(observation);

    var dateShiftMap = Map.of("tId-patient", "2000-01-20", "tId-obs", "2024-03-25T10:00:00Z");

    restoreShiftedDates(bundle, dateShiftMap);

    assertThat(patient.getBirthDateElement().getValueAsString()).isEqualTo("2000-01-20");
    assertThat(((DateTimeType) observation.getEffective()).getValueAsString())
        .isEqualTo("2024-03-25T10:00:00Z");
  }

  @Test
  void restoreShiftedDatesSkipsNullResourceEntries() {
    var bundle = new Bundle();
    bundle.addEntry().setResource(null);

    var patient = new Patient();
    var birthDate = new DateType("2000-01-01");
    birthDate.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("tId-patient"));
    patient.setBirthDateElement(birthDate);
    bundle.addEntry().setResource(patient);

    var dateShiftMap = Map.of("tId-patient", "2000-01-15");

    restoreShiftedDates(bundle, dateShiftMap);

    assertThat(patient.getBirthDateElement().getValueAsString()).isEqualTo("2000-01-15");
  }

  @Test
  void restoreShiftedDatesHandlesNestedExtensionWithDate() {
    var bundle = new Bundle();
    var patient = new Patient();

    // Create a nested extension structure: Patient -> Extension -> nested Extension -> date value
    var outerExtension = new Extension("http://example.org/outer");
    var dateValue = new DateTimeType("2024-06-01T10:00:00Z");
    dateValue.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("tId-nested"));
    var innerExtension = new Extension("http://example.org/inner", dateValue);
    outerExtension.addExtension(innerExtension);
    patient.addExtension(outerExtension);

    bundle.addEntry().setResource(patient);

    var dateShiftMap = Map.of("tId-nested", "2024-06-15T10:00:00Z");

    restoreShiftedDates(bundle, dateShiftMap);

    var restored =
        (DateTimeType)
            patient
                .getExtensionByUrl("http://example.org/outer")
                .getExtensionByUrl("http://example.org/inner")
                .getValue();
    assertThat(restored.getValueAsString()).isEqualTo("2024-06-15T10:00:00Z");
    assertThat(restored.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNull();
  }

  @Test
  void restoreShiftedDatesHandlesNestedElementsInEncounterLocation() {
    var bundle = new Bundle();
    var encounter = new Encounter();

    var period = new Period();
    var start = new DateTimeType("2024-01-01T08:00:00Z");
    start.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("tId-loc-start"));
    var end = new DateTimeType("2024-01-01T17:00:00Z");
    end.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("tId-loc-end"));
    period.setStartElement(start);
    period.setEndElement(end);

    encounter.addLocation().setPeriod(period);
    bundle.addEntry().setResource(encounter);

    var dateShiftMap =
        Map.of("tId-loc-start", "2024-01-05T08:00:00Z", "tId-loc-end", "2024-01-05T17:00:00Z");

    restoreShiftedDates(bundle, dateShiftMap);

    var restoredPeriod = encounter.getLocationFirstRep().getPeriod();
    assertThat(restoredPeriod.getStartElement().getValueAsString())
        .isEqualTo("2024-01-05T08:00:00Z");
    assertThat(restoredPeriod.getEndElement().getValueAsString()).isEqualTo("2024-01-05T17:00:00Z");
    assertThat(restoredPeriod.getStartElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL))
        .isNull();
    assertThat(restoredPeriod.getEndElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNull();
  }
}
