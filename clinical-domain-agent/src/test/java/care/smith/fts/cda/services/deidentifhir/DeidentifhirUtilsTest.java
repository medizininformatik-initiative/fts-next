package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils.generateRegistry;
import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.typesafe.config.ConfigFactory.parseResources;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.ConsentedPatient;
import de.ume.deidentifhir.Registry;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DeidentifhirUtilsTest {

  @Autowired MeterRegistry meterRegistry;

  @Test
  void deidentify() throws IOException {
    ConsentedPatient patient = new ConsentedPatient("id1");

    Map<String, String> transportIDs =
        Map.of(
            "id1.identifier.identifierSystem1:id1", "tid1.identifier.identifierSystem1:tid1",
            "id1.Patient:id1", "tid1.Patient:tid1");

    Registry registry = generateRegistry(patient.id(), transportIDs, Duration.ofMillis(1000));

    var config = parseResources(DeidentifhirUtilsTest.class, "CDtoTransport.profile");

    var bundle = generateOnePatient("id1", "2023", "identifierSystem1");
    Bundle deidentifiedBundle =
        DeidentifhirUtils.deidentify(config, registry, bundle, patient.id(), meterRegistry);
    Bundle b = (Bundle) deidentifiedBundle.getEntryFirstRep().getResource();

    Patient p = (Patient) b.getEntryFirstRep().getResource();

    assertThat(p.getId()).isEqualTo("Patient/tid1.Patient:tid1");
    assertThat(p.getIdentifierFirstRep().getValue())
        .isEqualTo("tid1.identifier.identifierSystem1:tid1");
  }
}
