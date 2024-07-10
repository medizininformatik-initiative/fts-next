package care.smith.fts.cda.rest.it;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.Status;
import java.io.IOException;
import java.time.Duration;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DeidentifhirIT extends TransferProcessControllerIT {
  private static final String patientId = "patientId";
  private static Bundle patient;

  public DeidentifhirIT() throws IOException {
    patient = generateOnePatient(patientId, "2025", DEFAULT_IDENTIFIER_SYSTEM);
  }

  @BeforeEach
  void setUp() throws IOException {
    mockCohortSelector.successOnePatient(patientId);
    mockDataSelector.getMockFhirResolveService().success(patientId, DEFAULT_IDENTIFIER_SYSTEM);
    mockDataSelector.whenFetchData(patientId).respondWith(patient);
  }

  @Test
  void tcaDown() {
    mockDataSelector.getMockTransportIds().isDown();
    startProcess(
        Duration.ofSeconds(3),
        r -> {
          assertThat(r.status()).isEqualTo(Status.COMPLETED);
          assertThat(r.bundlesSentCount()).isEqualTo(0);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void tcaTimeout() {
    mockDataSelector.getMockTransportIds().timeout();
    startProcess(
        Duration.ofSeconds(11),
        r -> {
          assertThat(r.status()).isEqualTo(Status.COMPLETED);
          assertThat(r.bundlesSentCount()).isEqualTo(0);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void unknownDomain() throws IOException {
    mockDataSelector.getMockTransportIds().unknownDomain(om, patientId, DEFAULT_IDENTIFIER_SYSTEM);
    startProcess(
        Duration.ofSeconds(3),
        r -> {
          assertThat(r.status()).isEqualTo(Status.COMPLETED);
          assertThat(r.bundlesSentCount()).isEqualTo(0);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }
}
