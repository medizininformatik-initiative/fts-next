package care.smith.fts.cda.rest.it;

import static care.smith.fts.test.TestPatientGenerator.generateNPatients;

import care.smith.fts.cda.TransferProcessRunner.Phase;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;

public class CohortSelectorIT extends TransferProcessControllerIT {

  @Test
  void tcaDown() {
    mockCohortSelector.isDown();

    startProcess(Duration.ofSeconds(3))
        .assertNext(r -> expectPhase(r, Phase.ERROR))
        .verifyComplete();
    startProcessForIds(Duration.ofSeconds(3), List.of())
        .assertNext(r -> expectPhase(r, Phase.ERROR))
        .verifyComplete();
  }

  @Test
  void tcaTimeoutConsentedPatientsRequest() {
    mockCohortSelector.timeout();

    startProcess(Duration.ofMinutes(1))
        .assertNext(r -> expectPhase(r, Phase.ERROR))
        .verifyComplete();
    startProcessForIds(Duration.ofMinutes(1), List.of())
        .assertNext(r -> expectPhase(r, Phase.ERROR))
        .verifyComplete();
  }

  @Test
  void tcaSendsWrongContentType() throws IOException {
    mockCohortSelector.wrongContentType();

    startProcess(Duration.ofSeconds(3))
        .assertNext(r -> expectPhase(r, Phase.ERROR))
        .verifyComplete();
    startProcessForIds(Duration.ofSeconds(3), List.of())
        .assertNext(r -> expectPhase(r, Phase.ERROR))
        .verifyComplete();
  }

  @Test
  void unknownDomain() {
    mockCohortSelector.unknownDomain(om);

    startProcess(Duration.ofSeconds(3))
        .assertNext(r -> expectPhase(r, Phase.ERROR))
        .verifyComplete();
    startProcessForIds(Duration.ofSeconds(3), List.of())
        .assertNext(r -> expectPhase(r, Phase.ERROR))
        .verifyComplete();
  }

  @Test
  void firstRequestToCohortFails() throws IOException {
    var idPrefix = "patientId";
    int total = 3;
    var patientsAndIds = generateNPatients(idPrefix, "2025", DEFAULT_IDENTIFIER_SYSTEM, total);
    var patients = patientsAndIds.bundle();
    var ids = patientsAndIds.ids();

    mockCohortSelector.consentForNPatients(idPrefix, total, List.of(500));
    for (var i = 0; i < patients.getTotal(); i++) {
      var patientId = ids.get(i);
      mockDataSelector.whenTransportMapping(patientId, DEFAULT_IDENTIFIER_SYSTEM).success();
      mockDataSelector
          .whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM)
          .resolveId(patientId);
      mockDataSelector
          .whenFetchData(patientId)
          .respondWith(new Bundle().addEntry(patients.getEntry().get(i)));
    }

    mockBundleSender.success();

    startProcess(Duration.ofSeconds(12))
        .assertNext(r -> completedWithBundles(total, r))
        .verifyComplete();
    startProcessForIds(Duration.ofSeconds(12), ids)
        .assertNext(r -> completedWithBundles(total, r))
        .verifyComplete();
  }

  @Test
  void someRequestsToCohortFailDuringPaging() throws IOException {
    var idPrefix = "patientId";
    int total = 7;
    int maxPageSize = 2;
    var patientsAndIds = generateNPatients(idPrefix, "2025", DEFAULT_IDENTIFIER_SYSTEM, total);
    var patients = patientsAndIds.bundle();
    var ids = patientsAndIds.ids();

    mockCohortSelector.consentForNPatientsWithPaging(
        idPrefix, total, maxPageSize, List.of(200, 500, 500, 200, 200, 500, 200));
    for (var i = 0; i < patients.getTotal(); i++) {
      var patientId = ids.get(i);
      mockDataSelector.whenTransportMapping(patientId, DEFAULT_IDENTIFIER_SYSTEM).success();
      mockDataSelector
          .whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM)
          .resolveId(patientId);
      mockDataSelector
          .whenFetchData(patientId)
          .respondWith(new Bundle().addEntry(patients.getEntry().get(i)));
    }

    mockBundleSender.success();

    startProcess(Duration.ofSeconds(8))
        .assertNext(r -> completedWithBundles(total, r))
        .verifyComplete();
    startProcessForIds(Duration.ofSeconds(8), ids)
        .assertNext(r -> completedWithBundles(total, r))
        .verifyComplete();
  }
}
