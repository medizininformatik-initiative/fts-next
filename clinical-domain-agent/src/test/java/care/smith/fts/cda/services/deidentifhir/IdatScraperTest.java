package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.typesafe.config.ConfigFactory.parseResources;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.ConsentedPatient;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdatScraperTest {
  private IdatScraper scraper;
  private PatientCompartmentService patientCompartmentService;

  @BeforeEach
  void setUp() {
    ConsentedPatient patient = new ConsentedPatient("id1", "identifierSystem1");
    var config = parseResources(IdatScraperTest.class, "IDScraper.profile");

    // Patient resource is in compartment (IS the patient)
    Map<String, List<String>> compartmentParams =
        Map.of(
            "Patient", List.of("link"),
            "ServiceRequest", List.of("subject", "performer"),
            "Organization", List.of());
    patientCompartmentService = new PatientCompartmentService(compartmentParams);

    scraper = new IdatScraper(config, patient, patientCompartmentService, "id1", false);
  }

  @Test
  void gatherIDs() throws IOException {
    var bundle = generateOnePatient("id1", "2023", "identifierSystem1", "identifier1");
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
