package care.smith.fts.cda.rest.it;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
public class DeidentifhirIT extends TransferProcessControllerIT {
  private static final String patientId = "patientId";
  private static Bundle patient;

  public DeidentifhirIT() throws IOException {
    patient = generateOnePatient(patientId, "2025", DEFAULT_IDENTIFIER_SYSTEM);
    log.trace("patient: {}", patient.getIdPart());
  }

  @BeforeEach
  void setUp() throws IOException {
    mockCohortSelector.consentForOnePatient(patientId);
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).resolveId(patientId);
    mockDataSelector.whenFetchData(patientId).respondWith(patient);
  }

  @Test
  void tcaDown() throws JsonProcessingException {
    mockDataSelector.whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM).isDown();
    startProcessExpectCompletedWithSkipped(Duration.ofSeconds(3));
  }

  @Test
  void tcaTimeout() throws JsonProcessingException {
    mockDataSelector.whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM).timeout();
    startProcessExpectCompletedWithSkipped(Duration.ofSeconds(11));
  }

  @Test
  void unknownDomain() throws IOException {
    mockDataSelector.whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM).unknownDomain(om);
    startProcessExpectCompletedWithSkipped(Duration.ofSeconds(3));
  }

  @Test
  void firstRequestToGetTransportIdFails() throws IOException {
    mockDataSelector
        .whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM)
        .successWithStatusCode(List.of(500));
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).resolveId(patientId);
    mockDataSelector
        .whenFetchData(patientId)
        .respondWith(new Bundle().addEntry(patient.getEntryFirstRep()));
    mockBundleSender.success();

    successfulRequest(Duration.ofSeconds(3), 1);
  }
}
