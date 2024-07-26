package care.smith.fts.cda.rest.it;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.Phase;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FhirResolveServiceIT extends TransferProcessControllerIT {
  private static final String patientId = "patientId";

  @BeforeEach
  void setUp() throws IOException {
    mockCohortSelector.consentForOnePatient(patientId);
  }

  @Test
  void hdsDown() {
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).isDown();
    startProcess(
        Duration.ofSeconds(1),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void hdsTimeout() {
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).timeout();
    startProcess(
        Duration.ofSeconds(11),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void hdsReturnsWrongContentType() {
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).wrongContentType();
    startProcess(
        Duration.ofSeconds(1),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void hdsReturnsMoreThanOneResult() throws IOException {
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).moreThanOneResult();
    startProcess(
        Duration.ofSeconds(1),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void hdsReturnsEmptyBundle() {
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).emptyBundle();
    startProcess(
        Duration.ofSeconds(1),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void hdsFirstRequestFails() throws IOException {
    var patient = generateOnePatient(patientId, "2025", DEFAULT_IDENTIFIER_SYSTEM);
    mockDataSelector.whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM).success();
    mockDataSelector
        .whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM)
        .resolveId(patientId, List.of(500));
    mockDataSelector
        .whenFetchData(patientId)
        .respondWith(new Bundle().addEntry(patient.getEntryFirstRep()));
    mockBundleSender.success();

    successfulRequest(Duration.ofSeconds(3), 1);
  }
}
