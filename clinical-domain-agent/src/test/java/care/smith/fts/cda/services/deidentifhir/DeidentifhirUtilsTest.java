package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils.generateRegistry;
import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.typesafe.config.ConfigFactory.parseResources;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.util.tca.IDMap;
import de.ume.deidentifhir.Registry;
import java.io.IOException;
import java.time.Duration;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class DeidentifhirUtilsTest {

  @Test
  void deidentify() throws IOException {
    ConsentedPatient patient = new ConsentedPatient("id1");

    IDMap transportIDs = new IDMap();
    transportIDs.put(
        "id1.identifier.identifierSystem1:id1", "tid1.identifier.identifierSystem1:tid1");
    transportIDs.put("id1.id.Patient:id1", "tid1.id.Patient:tid1");

    Registry registry = generateRegistry(patient.id(), transportIDs, Duration.ofMillis(1000));

    var config = parseResources(DeidentifhirUtilsTest.class, "CDtoTransport.profile");

    var bundle = generateOnePatient("id1", "2023", "identifierSystem1");
    Bundle deidentifiedBundle =
        DeidentifhirUtils.deidentify(config, registry, bundle, patient.id());
    Bundle b = (Bundle) deidentifiedBundle.getEntryFirstRep().getResource();
    Patient p = (Patient) b.getEntryFirstRep().getResource();

    assertThat(p.getId()).isEqualTo("Patient/tid1.id.Patient:tid1");
    assertThat(p.getIdentifierFirstRep().getValue())
        .isEqualTo("tid1.identifier.identifierSystem1:tid1");
  }
}
