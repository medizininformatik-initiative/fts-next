package care.smith.fts.cda.rest.it;

import static care.smith.fts.test.TestPatientGenerator.generateNPatients;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;

public class CohortSelectorIT extends TransferProcessControllerIT {

  @Test
  void tcaDown() {
    mockCohortSelector.isDown();
    startProcessExpectError(Duration.ofSeconds(1));
  }

  @Test
  void tcaTimeoutConsentedPatientsRequest() {
    mockCohortSelector.timeout();
    startProcessExpectError(Duration.ofSeconds(10));
  }

  @Test
  void tcaSendsWrongContentType() throws IOException {
    mockCohortSelector.wrongContentType();
    startProcessExpectError(Duration.ofMillis(200));
  }

  @Test
  void unknownDomain() throws JsonProcessingException {
    mockCohortSelector.unknownDomain(om);
    startProcessExpectError(Duration.ofMillis(200));
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
      mockDataSelector.whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM).success();
      mockDataSelector
          .whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM)
          .resolveId(patientId);
      mockDataSelector
          .whenFetchData(patientId)
          .respondWith(new Bundle().addEntry(patients.getEntry().get(i)));
    }

    mockBundleSender.success();
    successfulRequest(Duration.ofSeconds(4), total);
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
      mockDataSelector.whenTransportIds(patientId, DEFAULT_IDENTIFIER_SYSTEM).success();
      mockDataSelector
          .whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM)
          .resolveId(patientId);
      mockDataSelector
          .whenFetchData(patientId)
          .respondWith(new Bundle().addEntry(patients.getEntry().get(i)));
    }

    mockBundleSender.success();
    successfulRequest(Duration.ofSeconds(8), total);
  }
}
