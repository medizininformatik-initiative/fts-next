package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.typesafe.config.ConfigFactory.parseResources;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.ConsentedPatient;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IDATScraperTest {
  IDATScraper scraper;

  @BeforeEach
  void setUp() {
    ConsentedPatient patient = new ConsentedPatient("id1", "system");
    var config = parseResources(IDATScraperTest.class, "IDScraper.profile");
    scraper = new IDATScraper(config, patient);
  }

  @Test
  void gatherIDs() throws IOException {
    var bundle = generateOnePatient("id1", "2023", "identifierSystem1");
    assertThat(scraper.gatherIDs(bundle))
        .containsExactlyInAnyOrder("id1.identifier.identifierSystem1:id1", "id1.Patient:id1");
  }
}
