package care.smith.fts.cda.rest.it;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.Phase;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.time.Duration;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DeidentifhirIT extends TransferProcessDefinitionControllerIT {
  private static final String patientId = "patientId";
  private static Bundle patient;

  public DeidentifhirIT() throws IOException {
    patient = generateOnePatient(patientId, "2025", DEFAULT_IDENTIFIER_SYSTEM);
  }

  @BeforeEach
  void setUp() throws IOException {
    mockCohortSelector.successOnePatient(patientId);
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).success(patientId);
    mockDataSelector.whenFetchData(patientId).respondWith(patient);
  }

  @Test
  void tcaDown() throws JsonProcessingException {
    mockDataSelector.whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM).isDown();
    startProcess(
        Duration.ofSeconds(3),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.bundlesSentCount()).isEqualTo(0);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void tcaTimeout() throws JsonProcessingException {
    mockDataSelector.whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM).timeout();
    startProcess(
        Duration.ofSeconds(11),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.bundlesSentCount()).isEqualTo(0);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void unknownDomain() throws IOException {
    mockDataSelector
        .whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM)
        .unknownDomain(om, patientId, DEFAULT_IDENTIFIER_SYSTEM);
    startProcess(
        Duration.ofSeconds(3),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.bundlesSentCount()).isEqualTo(0);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }
}
