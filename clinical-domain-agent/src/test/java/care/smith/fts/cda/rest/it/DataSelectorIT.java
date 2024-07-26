package care.smith.fts.cda.rest.it;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DataSelectorIT extends TransferProcessControllerIT {
  private static final String patientId = "patientId";

  @BeforeEach
  void setUp() throws IOException {
    mockCohortSelector.consentForOnePatient(patientId);
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).resolveId(patientId);
  }

  @Test
  void hdsDown() {
    mockDataSelector.whenFetchData(patientId).dropConnection();
    startProcessExpectCompletedWithSkipped(Duration.ofSeconds(1));
  }

  @Test
  void hdsTimeout() {
    mockDataSelector.whenFetchData(patientId).timeout();
    startProcessExpectCompletedWithSkipped(Duration.ofSeconds(11));
  }

  @Test
  void hdsReturnsWrongContentType() {
    mockDataSelector.whenFetchData(patientId).respondWithWrongContentType();
    startProcessExpectCompletedWithSkipped(Duration.ofSeconds(1));
  }

  @Test
  void hdsReturnsEmptyBundle() {
    mockDataSelector.whenFetchData(patientId).respondWithEmptyBundle();
    startProcessExpectCompletedWithSkipped(Duration.ofSeconds(1));
  }

  @Test
  void hdsFirstRequestFails() throws IOException {
    var patient = generateOnePatient(patientId, "2025", DEFAULT_IDENTIFIER_SYSTEM);
    mockDataSelector.whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM).success();
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).resolveId(patientId);
    mockDataSelector
        .whenFetchData(patientId)
        .respondWith(new Bundle().addEntry(patient.getEntryFirstRep()), List.of(500));
    mockBundleSender.success();

    successfulRequest(Duration.ofSeconds(5), 1);
  }
}
