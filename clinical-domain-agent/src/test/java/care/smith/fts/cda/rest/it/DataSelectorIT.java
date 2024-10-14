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

    startProcess(Duration.ofSeconds(3))
        .assertNext(TransferProcessControllerIT::errored)
        .verifyComplete();
  }

  @Test
  void hdsTimeout() {
    mockDataSelector.whenFetchData(patientId).timeout();

    startProcess(Duration.ofMinutes(1))
        .assertNext(TransferProcessControllerIT::errored)
        .verifyComplete();
  }

  @Test
  void hdsReturnsWrongContentType() {
    mockDataSelector.whenFetchData(patientId).respondWithWrongContentType();
    startProcess(Duration.ofSeconds(3))
        .assertNext(TransferProcessControllerIT::errored)
        .verifyComplete();
  }

  @Test
  void hdsReturnsEmptyBundle() {
    mockDataSelector.whenFetchData(patientId).respondWithEmptyBundle();

    startProcess(Duration.ofSeconds(3))
        .assertNext(TransferProcessControllerIT::errored)
        .verifyComplete();
  }

  @Test
  void hdsFirstRequestFails() throws IOException {
    var patient = generateOnePatient(patientId, "2025", DEFAULT_IDENTIFIER_SYSTEM);
    mockDataSelector.whenTransportMapping(patientId, DEFAULT_IDENTIFIER_SYSTEM).success();
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).resolveId(patientId);
    mockDataSelector
        .whenFetchData(patientId)
        .respondWith(new Bundle().addEntry(patient.getEntryFirstRep()), List.of(500));
    mockBundleSender.success();

    startProcess(Duration.ofSeconds(5))
        .assertNext(r -> completedWithBundles(1, r))
        .verifyComplete();
  }
}
