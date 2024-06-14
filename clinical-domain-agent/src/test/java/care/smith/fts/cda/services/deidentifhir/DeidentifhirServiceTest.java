package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.util.tca.IDMap;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeidentifhirServiceTest {

  private DeidentifhirService DeidentifhirService;

  @BeforeEach
  void setUp() {
    ConsentedPatient patient =
        new ConsentedPatient("id1", new ConsentedPatient.ConsentedPolicies());

    var config =
        ConfigFactory.parseFile(
            new File(
                "src/test/resources/care/smith/fts/cda/services/deidentifhir/CDtoTransport.profile"));

    IDMap transportIDs = new IDMap();
    transportIDs.put(
        "id1.identifier.identifierSystem1:id1", "tid1.identifier.identifierSystem1:tid1");
    transportIDs.put("id1.id.Patient:id1", "tid1.id.Patient:tid1");

    DeidentifhirService =
        new DeidentifhirService(config, patient, transportIDs, Duration.ofMillis(1000));
  }

  @Test
  void deidentify() throws IOException {
    var bundle = generateOnePatient("id1", "2023", "identifierSystem1");
    Bundle deidentifiedBundle = (Bundle) DeidentifhirService.deidentify(bundle);
    Bundle b = (Bundle) deidentifiedBundle.getEntryFirstRep().getResource();
    Patient p = (Patient) b.getEntryFirstRep().getResource();

    assertThat(p.getId()).isEqualTo("Patient/tid1.id.Patient:tid1");
    assertThat(p.getIdentifierFirstRep().getValue())
        .isEqualTo("tid1.identifier.identifierSystem1:tid1");
  }
}
