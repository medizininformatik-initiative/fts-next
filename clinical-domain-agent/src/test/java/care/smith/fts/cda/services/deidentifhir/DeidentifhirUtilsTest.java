package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils.deidentify;
import static care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils.generateRegistry;
import static care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils.shiftDate;
import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_EXTENSION_URL;
import static com.typesafe.config.ConfigFactory.parseResources;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.test.TestPatientGenerator;
import de.ume.deidentifhir.Registry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

class DeidentifhirUtilsTest {

  private static final String MII_PATIENT_PROFILE =
      "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient";

  MeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

  @Test
  void deidentifySucceeds() throws IOException {
    ConsentedPatient patient = new ConsentedPatient("id1", "system");

    Map<String, String> transportIDs =
        Map.of(
            "id1.identifier.identifierSystem1:identifier1",
                "tid1.identifier.identifierSystem1:tidentifier1",
            "id1.Patient:id1", "tid1.Patient:tid1");

    Registry registry = generateRegistry(patient.identifier(), transportIDs, Map.of());

    var config = parseResources(DeidentifhirUtilsTest.class, "CDtoTransport.profile");

    var bundle =
        TestPatientGenerator.generateOnePatient("id1", "2023", "identifierSystem1", "identifier1");
    Bundle deidentifiedBundle =
        deidentify(config, registry, bundle, patient.identifier(), meterRegistry);
    Bundle b = (Bundle) deidentifiedBundle.getEntryFirstRep().getResource();

    Patient p = (Patient) b.getEntryFirstRep().getResource();

    assertThat(p.getId()).isEqualTo("Patient/tid1.Patient:tid1");
    assertThat(p.getIdentifierFirstRep().getValue())
        .isEqualTo("tid1.identifier.identifierSystem1:tidentifier1");
  }

  @Test
  void shiftDateReturnsNullWhenDateIsNull() {
    var result = shiftDate(null, Map.of("1950-01-01", "tId-birthDate"));

    assertThat(result).isNull();
  }

  @Test
  void shiftDateReturnsUnchangedWhenDateValueIsNull() {
    var date = new DateType();

    var result = shiftDate(date, Map.of("1950-01-01", "tId-birthDate"));

    assertThat(result).isSameAs(date);
    assertThat(result.getValue()).isNull();
    assertThat(result.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNull();
  }

  @Test
  void shiftDateAddsExtensionAndNullsValueWhenMappingExists() {
    var date = new DateType("1950-01-01");

    var result = shiftDate(date, Map.of("1950-01-01", "tId-birthDate"));

    assertThat(result.getValue()).isNull();
    assertThat(result.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNotNull();
    assertThat(
            ((StringType) result.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL).getValue()).getValue())
        .isEqualTo("tId-birthDate");
  }

  @Test
  void shiftDateReturnsUnchangedWhenNoMappingExists() {
    var date = new DateType("1950-01-01");
    var originalValue = date.getValue();

    var result = shiftDate(date, Map.of("2000-01-01", "tId-other"));

    assertThat(result).isSameAs(date);
    assertThat(result.getValue()).isEqualTo(originalValue);
    assertThat(result.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNull();
  }

  @Test
  void shiftDateWorksWithDateTimeType() {
    var dateTime = new DateTimeType("2024-03-15T10:00:00Z");

    var result = shiftDate(dateTime, Map.of("2024-03-15T10:00:00Z", "tId-datetime"));

    assertThat(result.getValue()).isNull();
    assertThat(result.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNotNull();
    assertThat(
            ((StringType) result.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL).getValue()).getValue())
        .isEqualTo("tId-datetime");
  }

  @Test
  void shiftDateWithEmptyMappingLeavesDateUnchanged() {
    var date = new DateType("1950-01-01");
    var originalValue = date.getValue();

    var result = shiftDate(date, Map.of());

    assertThat(result).isSameAs(date);
    assertThat(result.getValue()).isEqualTo(originalValue);
    assertThat(result.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNull();
  }
}
