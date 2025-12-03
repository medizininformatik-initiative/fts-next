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

  @Test
  void gatherIDs_singlePatientResource() throws IOException {
    // Single Patient resource (not in a bundle) - tests the non-bundle branch
    // with a resource that IS the patient (in compartment)
    var patientResource =
        generateOnePatient(PATIENT_ID, "2023", "identifierSystem1", "identifier1")
            .getEntryFirstRep()
            .getResource();

    var ids = scraper.gatherIDs(patientResource);

    // Patient IS the patient, so should have prefix
    assertThat(ids).contains("id1.Patient:id1");
  }

  @Test
  void gatherIDs_singlePatientResource_differentPatient() throws IOException {
    // Single Patient resource that is NOT the patient we're scraping for
    // Tests non-bundle branch with resource NOT in compartment
    var differentPatient =
        generateOnePatient("different-patient", "2023", "identifierSystem1", "identifier2")
            .getEntryFirstRep()
            .getResource();

    var ids = scraper.gatherIDs(differentPatient);

    // Different patient should NOT have the patient prefix (not in compartment)
    assertThat(ids).contains("Patient:different-patient");
    assertThat(ids).doesNotContain("id1.Patient:different-patient");
  }
}
