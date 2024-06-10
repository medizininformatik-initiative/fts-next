package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.test.FhirGenerator.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.test.FhirGenerator;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.*;
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
    FhirGenerator fhirGenerator = new FhirGenerator("PatientTemplate.json");
    fhirGenerator.replaceTemplateFieldWith("$YEAR", new Fixed("2023"));
    fhirGenerator.replaceTemplateFieldWith("$PATIENT_ID", new Fixed("id1"));

    Bundle bundle = fhirGenerator.generateBundle(1, 100);

    assertThat(scraper.gatherIDs(bundle))
        .containsExactlyInAnyOrder("id1.identifier.$IDENTIFIER_SYSTEM:id1", "id1.id.Patient:id1");
  }
}
