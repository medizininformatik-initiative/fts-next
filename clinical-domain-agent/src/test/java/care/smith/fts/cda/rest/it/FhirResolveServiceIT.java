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

    startProcess(Duration.ofSeconds(3))
        .assertNext(TransferProcessControllerIT::errored)
        .verifyComplete();
  }

  @Test
  void hdsTimeout() {
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).timeout();

    startProcess(Duration.ofMinutes(1))
        .assertNext(TransferProcessControllerIT::errored)
        .verifyComplete();
  }

  @Test
  void hdsReturnsWrongContentType() {
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).wrongContentType();

    startProcess(Duration.ofSeconds(3))
        .assertNext(TransferProcessControllerIT::errored)
        .verifyComplete();
  }

  @Test
  void hdsReturnsMoreThanOneResult() throws IOException {
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).moreThanOneResult();

    startProcess(Duration.ofSeconds(3))
        .assertNext(TransferProcessControllerIT::errored)
        .verifyComplete();
  }

  @Test
  void hdsReturnsEmptyBundle() {
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).emptyBundle();

    startProcess(Duration.ofSeconds(3))
        .assertNext(TransferProcessControllerIT::errored)
        .verifyComplete();
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

    startProcess(Duration.ofSeconds(3))
        .assertNext(r -> completedWithBundles(1, r))
        .verifyComplete();
  }
}
