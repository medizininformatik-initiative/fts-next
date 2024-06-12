package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.ConsentedPatient;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IDATScraperTest {
  IDATScraper scraper;

  @BeforeEach
  void setUp() {
    ConsentedPatient patient =
        new ConsentedPatient("id1", new ConsentedPatient.ConsentedPolicies());
    var config =
        ConfigFactory.parseFile(
            new File(
                "src/test/resources/care/smith/fts/cda/services/deidentifhir/IDScraper.profile"));
    scraper = new IDATScraper(config, patient);
  }

  @Test
  void gatherIDs() throws IOException {
    var bundle = generateOnePatient("id1", "2023", "identifierSystem1");
    assertThat(scraper.gatherIDs(bundle))
        .containsExactlyInAnyOrder("id1.identifier.identifierSystem1:id1", "id1.id.Patient:id1");
  }
}
