package care.smith.fts.cda.rest.it;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;

import java.io.IOException;
import java.time.Duration;
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
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).success(patientId);
    mockDataSelector.whenFetchData(patientId).respondWith(patient);
    mockDataSelector.whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM).success();
  }

  @Test
  void hdsDown() {
    mockBundleSender.isDown();
    startProcessExpectCompletedWithSkipped(Duration.ofSeconds(1));
  }

  @Test
  void hdsTimeout() {
    mockBundleSender.timeout();
    startProcessExpectCompletedWithSkipped(Duration.ofSeconds(12));
  }
}
