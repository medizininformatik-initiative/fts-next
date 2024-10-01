package care.smith.fts.cda.rest.it;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BundleSenderIT extends TransferProcessControllerIT {
  private static final String patientId = "patientId";
  private static Bundle patient;

  public BundleSenderIT() throws IOException {
    patient = generateOnePatient(patientId, "2025", DEFAULT_IDENTIFIER_SYSTEM);
  }

  @BeforeEach
  void setUp() throws IOException {
    mockCohortSelector.consentForOnePatient(patientId);
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).resolveId(patientId);
    mockDataSelector.whenFetchData(patientId).respondWith(patient);
    mockDataSelector.whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM).success();
  }

  @Test
  void rdaDown() {
    mockBundleSender.isDown();

    startProcess(Duration.ofSeconds(3))
        .assertNext(TransferProcessControllerIT::errored)
        .verifyComplete();
  }

  @Test
  void rdaTimeout() {
    mockBundleSender.timeout();

    startProcess(Duration.ofMinutes(1))
        .assertNext(TransferProcessControllerIT::errored)
        .verifyComplete();
  }

  @Test
  void firstTryToSendBundleFails() throws IOException {
    mockDataSelector.whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM).success();
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).resolveId(patientId);
    mockDataSelector
        .whenFetchData(patientId)
        .respondWith(new Bundle().addEntry(patient.getEntryFirstRep()));
    mockBundleSender.successWithStatusCode(List.of(500));

    startProcess(Duration.ofSeconds(5))
        .assertNext(r -> completedWithBundles(1, r))
        .verifyComplete();
  }
}
