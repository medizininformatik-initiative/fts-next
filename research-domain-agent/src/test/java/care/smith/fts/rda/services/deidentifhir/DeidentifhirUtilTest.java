package care.smith.fts.rda.services.deidentifhir;

import static care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil.generateRegistry;
import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.typesafe.config.ConfigFactory.parseResources;
import static org.assertj.core.api.Assertions.assertThat;

import de.ume.deidentifhir.Registry;
import java.io.IOException;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class DeidentifhirUtilTest {

  @Test
  void replaceIDs() throws IOException {
    Map<String, String> transportIDs = Map.of("tid1", "pid1");

    Registry registry = generateRegistry(transportIDs);
    var config = parseResources(DeidentifhirUtilTest.class, "TransportToRD.profile");
    var transportBundle = generateOnePatient("tid1", "2023", "identifierSystem1");

    var pseudomizedBundle = DeidentifhirUtil.replaceIDs(config, registry, transportBundle);

    Bundle b = (Bundle) pseudomizedBundle.getEntryFirstRep().getResource();
    Patient p = (Patient) b.getEntryFirstRep().getResource();

    assertThat(pseudomizedBundle.getEntry()).hasSize(1);
    assertThat(b.getEntry()).hasSize(1);
    assertThat(p.getId()).isEqualTo("Patient/pid1");
    assertThat(p.getIdentifierFirstRep().getValue()).isEqualTo("pid1");
  }
}
