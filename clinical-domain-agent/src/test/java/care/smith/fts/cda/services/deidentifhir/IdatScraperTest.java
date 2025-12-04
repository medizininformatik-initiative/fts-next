package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.typesafe.config.ConfigFactory.parseResources;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.cda.services.PatientCompartmentService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdatScraperTest {
  private static final String PATIENT_ID = "id1";
  private IdatScraper scraper;
  private CompartmentMembershipChecker compartmentChecker;

  @BeforeEach
  void setUp() {
    ConsentedPatient patient = new ConsentedPatient(PATIENT_ID, "identifierSystem1");
    var config = parseResources(IdatScraperTest.class, "IDScraper.profile");

    // Patient resource is in compartment (IS the patient)
    var compartmentService =
        new PatientCompartmentService(
            Map.of(
                "Patient", List.of("link"),
                "ServiceRequest", List.of("subject", "performer"),
                "Organization", List.of()));
    compartmentChecker = new CompartmentMembershipChecker(compartmentService);

    scraper = new IdatScraper(config, patient, compartmentChecker, PATIENT_ID);
  }

  @Test
  void gatherIDs() throws IOException {
    var bundle = generateOnePatient(PATIENT_ID, "2023", "identifierSystem1", "identifier1");
    assertThat(scraper.gatherIDs(bundle))
        .containsExactlyInAnyOrder(
            "id1.identifier.identifierSystem1:identifier1", "id1.Patient:id1");
  }

  @Test
  void gatherIDs_emptyBundle() {
    var bundle = new Bundle();
    bundle.setType(Bundle.BundleType.COLLECTION);

    var ids = scraper.gatherIDs(bundle);

    assertThat(ids).isEmpty();
  }
}
