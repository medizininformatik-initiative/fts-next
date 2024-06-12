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
  private Bundle bundle;

  @BeforeEach
  void setUp() throws IOException {
    ConsentedPatient patient =
        new ConsentedPatient("id1", new ConsentedPatient.ConsentedPolicies());
    FhirGenerator fhirGenerator = new FhirGenerator("PatientTemplate.json");
    fhirGenerator.replaceTemplateFieldWith("$YEAR", new Fixed("2023"));
    fhirGenerator.replaceTemplateFieldWith("$PATIENT_ID", new Fixed("id1"));
    fhirGenerator.replaceTemplateFieldWith(
        "$IDENTIFIER_SYSTEM", new FhirGenerator.Fixed("urn:oid:1.2.36.146.595.217.0.1"));
    bundle = fhirGenerator.generateBundle(1, 100);
    var config =
        ConfigFactory.parseFile(
            new File(
                "src/test/resources/care/smith/fts/cda/services/deidentifhir/IDScraper.profile"));
    scraper = new IDATScraper(config, patient);
  }

  @Test
  void gatherIDs() {
    assertThat(scraper.gatherIDs(bundle))
        .containsExactlyInAnyOrder("id1.identifier.$IDENTIFIER_SYSTEM:id1", "id1.id.Patient:id1");
  }
}
