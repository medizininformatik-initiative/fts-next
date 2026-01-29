package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.typesafe.config.ConfigFactory.parseResources;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.ConsentedPatient;
import java.io.IOException;
import java.util.Date;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataScraperTest {
  DataScraper scraper;

  @BeforeEach
  void setUp() {
    ConsentedPatient patient = new ConsentedPatient("id1", "identifierSystem1");
    var config = parseResources(DataScraperTest.class, "CDtoTransport.profile");
    scraper = new DataScraper(config, patient);
  }

  @Test
  void generalizeDateHandlerReturnsDateUnchanged() {
    // Uses generalizeDateHandler (not shiftDateHandler), so no tID mapping is created
    // and date values remain unchanged
    var config =
        parseResources(DataScraperTest.class, "CDtoTransportWithGeneralizeDateHandler.profile");
    var scraperWithGeneralize = new DataScraper(config, new ConsentedPatient("id1", "sys1"));

    var patient = new Patient();
    patient.setId("id1");
    patient.setMeta(
        new Meta()
            .addProfile(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient"));
    patient.addIdentifier(new Identifier().setSystem("sys1").setValue("val1"));
    patient.setBirthDate(new Date());

    var bundle = new Bundle();
    bundle.addEntry().setResource(patient);

    var scrapedData = scraperWithGeneralize.scrape(bundle);

    // generalizeDateHandler is a no-op that doesn't collect dates for transport ID mapping
    assertThat(scrapedData.dateTransportMappings()).isEmpty();
    // IDs should still be gathered
    assertThat(scrapedData.ids()).contains("id1.Patient:id1");
  }

  @Test
  void gatherIDs() throws IOException {
    var bundle = generateOnePatient("id1", "2023", "identifierSystem1", "identifier1");
    var scrapedData = scraper.scrape(bundle);
    assertThat(scrapedData.ids())
        .containsExactlyInAnyOrder(
            "id1.identifier.identifierSystem1:identifier1", "id1.Patient:id1");
  }

  @Test
  void gatherDatesWithTransportIds() throws IOException {
    var bundle = generateOnePatient("id1", "2023", "identifierSystem1", "identifier1");
    var scrapedData = scraper.scrape(bundle);

    // Verify tID→originalDate mapping is returned (Patient.birthDate is 1950-01-01)
    assertThat(scrapedData.dateTransportMappings()).hasSize(1);
    assertThat(scrapedData.dateTransportMappings().values()).containsExactly("1950-01-01");

    // Verify each mapping has a non-empty transport ID
    scrapedData.dateTransportMappings().keySet().forEach(tId -> assertThat(tId).isNotEmpty());
  }

  @Test
  void transportIdsAreUnique() throws IOException {
    ConsentedPatient patient = new ConsentedPatient("id1", "identifierSystem1");
    var config = parseResources(DataScraperTest.class, "CDtoTransport.profile");
    var bundle = generateOnePatient("id1", "2023", "identifierSystem1", "identifier1");

    // Each DataScraper instance generates unique transport IDs
    var scraper1 = new DataScraper(config, patient);
    var scraper2 = new DataScraper(config, patient);
    var scrapedData1 = scraper1.scrape(bundle);
    var scrapedData2 = scraper2.scrape(bundle);

    var tIds1 = scrapedData1.dateTransportMappings().keySet();
    var tIds2 = scrapedData2.dateTransportMappings().keySet();
    assertThat(tIds1).doesNotContainAnyElementsOf(tIds2);
  }

  @Test
  void transportIdFormat() throws IOException {
    var bundle = generateOnePatient("id1", "2023", "identifierSystem1", "identifier1");
    var scrapedData = scraper.scrape(bundle);

    // Transport IDs use NanoId default (21 chars)
    scrapedData
        .dateTransportMappings()
        .keySet()
        .forEach(
            tId -> {
              assertThat(tId).hasSize(21);
              assertThat(tId).matches("^[A-Za-z0-9_-]+$");
            });
  }

  @Test
  void gatherBothIdsAndDates() throws IOException {
    var bundle = generateOnePatient("id1", "2023", "identifierSystem1", "identifier1");
    var scrapedData = scraper.scrape(bundle);
    assertThat(scrapedData.ids()).hasSize(2);
    assertThat(scrapedData.dateTransportMappings()).hasSize(1);
  }

  @Test
  void handlesPatientWithNullBirthDateValue() {
    var patient = new Patient();
    patient.setId("id1");
    patient.setMeta(
        new Meta()
            .addProfile(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient"));
    patient.addIdentifier(new Identifier().setSystem("identifierSystem1").setValue("identifier1"));
    patient.setBirthDateElement(new DateType());

    var bundle = new Bundle();
    bundle.addEntry().setResource(patient);

    var scrapedData = scraper.scrape(bundle);
    assertThat(scrapedData.dateTransportMappings()).isEmpty();
    assertThat(scrapedData.ids())
        .containsExactlyInAnyOrder(
            "id1.identifier.identifierSystem1:identifier1", "id1.Patient:id1");
  }

  @Test
  void handlesPatientWithNoBirthDateElement() {
    var patient = new Patient();
    patient.setId("id1");
    patient.setMeta(
        new Meta()
            .addProfile(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient"));
    patient.addIdentifier(new Identifier().setSystem("identifierSystem1").setValue("identifier1"));
    // No birthDate set at all - tests the `date != null` check

    var bundle = new Bundle();
    bundle.addEntry().setResource(patient);

    var scrapedData = scraper.scrape(bundle);
    assertThat(scrapedData.dateTransportMappings()).isEmpty();
    assertThat(scrapedData.ids())
        .containsExactlyInAnyOrder(
            "id1.identifier.identifierSystem1:identifier1", "id1.Patient:id1");
  }

  @Test
  void duplicateDateValuesGenerateSingleTransportId() {
    var encounter = new Encounter();
    encounter.setId("enc1");
    encounter.setMeta(
        new Meta()
            .addProfile(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung"));
    encounter.setSubject(new Reference("Patient/id1"));
    var sameDate = new Date();
    encounter.setPeriod(new Period().setStart(sameDate).setEnd(sameDate));

    var bundle = new Bundle();
    bundle.addEntry().setResource(encounter);

    var scrapedData = scraper.scrape(bundle);

    // Both period.start and period.end have the same date, should generate only one tID
    assertThat(scrapedData.dateTransportMappings()).hasSize(1);
  }
}
