package care.smith.fts.cda.rest.it;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.Phase;
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
    mockCohortSelector.successOnePatient(patientId);
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).success(patientId);
    mockDataSelector.whenFetchData(patientId).respondWith(patient);
    mockDataSelector.whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM).success();
  }

  @Test
  void hdsDown() {
    mockBundleSender.isDown();
    startProcess(
        Duration.ofSeconds(1),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.bundlesSentCount()).isEqualTo(0);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void hdsTimeout() {
    mockBundleSender.timeout();
    startProcess(
        Duration.ofSeconds(12),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.bundlesSentCount()).isEqualTo(0);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }
}
