package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.typesafe.config.ConfigFactory.parseResources;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.ConsentedPatient;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdatScraperTest {
  IdatScraper scraper;

  @BeforeEach
  void setUp() {
    ConsentedPatient patient = new ConsentedPatient("id1", "identifierSystem1");
    var config = parseResources(IdatScraperTest.class, "CDtoTransport.profile");
    scraper = new IdatScraper(config, patient);
  }

  @Test
  void gatherIDs() throws IOException {
    var bundle = generateOnePatient("id1", "2023", "identifierSystem1", "identifier1");
    assertThat(scraper.gatherIDs(bundle))
        .containsExactlyInAnyOrder(
            "id1.identifier.identifierSystem1:identifier1", "id1.Patient:id1");
  }
}
