package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils.buildRegistry;
import static care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils.deidentify;
import static care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils.shiftDate;
import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_EXTENSION_URL;
import static com.typesafe.config.ConfigFactory.parseResources;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.test.TestPatientGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeidentifhirUtilsTest {

  MeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
  GeneratingReplacementProvider provider;

  @BeforeEach
  void setUp() {
    provider = new GeneratingReplacementProvider("id1");
  }

  @Test
  void deidentifySucceeds() throws IOException {
    ConsentedPatient patient = new ConsentedPatient("id1", "system");
    var registry = buildRegistry(provider);
    var config = parseResources(DeidentifhirUtilsTest.class, "CDtoTransport.profile");

    var bundle =
        TestPatientGenerator.generateOnePatient("id1", "2023", "identifierSystem1", "identifier1");
    Bundle deidentifiedBundle =
        deidentify(config, registry, bundle, patient.identifier(), meterRegistry);
    Bundle b = (Bundle) deidentifiedBundle.getEntryFirstRep().getResource();

    Patient p = (Patient) b.getEntryFirstRep().getResource();

    // Verify the ID was replaced with a generated tID
    assertThat(p.getId()).startsWith("Patient/");
    assertThat(p.getId()).isNotEqualTo("Patient/id1");

    // Verify the identifier value was replaced with a generated tID
    assertThat(p.getIdentifierFirstRep().getValue()).isNotEqualTo("identifier1");
    assertThat(p.getIdentifierFirstRep().getValue()).hasSize(21); // NanoId length
  }

  @Test
  void shiftDateReturnsNullWhenDateIsNull() {
    var result = shiftDate(null, provider);

    assertThat(result).isNull();
  }

  @Test
  void shiftDateReturnsUnchangedWhenDateValueIsNull() {
    var date = new DateType();

    var result = shiftDate(date, provider);

    assertThat(result).isSameAs(date);
    assertThat(result.getValue()).isNull();
    assertThat(result.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNull();
  }

  @Test
  void shiftDateAddsExtensionAndNullsValue() {
    var date = new DateType("1950-01-01");

    var result = shiftDate(date, provider);

    assertThat(result.getValue()).isNull();
    assertThat(result.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNotNull();
    var tId =
        ((StringType) result.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL).getValue()).getValue();
    assertThat(tId).hasSize(21); // NanoId length
    assertThat(provider.getDateMappings()).containsEntry(tId, "1950-01-01");
  }

  @Test
  void shiftDateWorksWithDateTimeType() {
    var dateTime = new DateTimeType("2024-03-15T10:00:00Z");

    var result = shiftDate(dateTime, provider);

    assertThat(result.getValue()).isNull();
    assertThat(result.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNotNull();
    var tId =
        ((StringType) result.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL).getValue()).getValue();
    assertThat(tId).hasSize(21);
    assertThat(provider.getDateMappings()).containsEntry(tId, "2024-03-15T10:00:00Z");
  }

  @Test
  void shiftDateDeduplicatesSameDateValue() {
    var date1 = new DateType("1950-01-01");
    var date2 = new DateType("1950-01-01");

    shiftDate(date1, provider);
    shiftDate(date2, provider);

    // Only one mapping should exist for the same date value
    assertThat(provider.getDateMappings()).hasSize(1);
    assertThat(provider.getDateMappings()).containsValue("1950-01-01");
  }
}
