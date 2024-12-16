package care.smith.fts.cda.rest.it;

import static care.smith.fts.test.TestPatientGenerator.generateNPatients;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError;

public class CohortSelectorIT extends TransferProcessControllerIT {

  @Test
  void tcaDown() {
    allCohortSelector.isDown();
    listCohortSelector.isDown();

    startProcess(Duration.ofSeconds(3)).expectError(InternalServerError.class).verify();
    startProcessForIds(Duration.ofSeconds(3), List.of())
        .expectError(InternalServerError.class)
        .verify();
  }

  @Test
  void tcaTimeoutConsentedPatientsRequest() {
    allCohortSelector.timeout();
    listCohortSelector.timeout();

    startProcess(Duration.ofMinutes(1)).expectError(InternalServerError.class).verify();
    startProcessForIds(Duration.ofMinutes(1), List.of())
        .expectError(InternalServerError.class)
        .verify();
  }

  @Test
  void tcaSendsWrongContentType() {
    allCohortSelector.wrongContentType();
    listCohortSelector.wrongContentType();

    startProcess(Duration.ofSeconds(3)).expectError(InternalServerError.class).verify();
    startProcessForIds(Duration.ofSeconds(3), List.of())
        .expectError(InternalServerError.class)
        .verify();
  }

  @Test
  void unknownDomain() throws JsonProcessingException {
    allCohortSelector.unknownDomain(om);
    listCohortSelector.unknownDomain(om);

    startProcess(Duration.ofSeconds(3)).expectError(InternalServerError.class).verify();
    startProcessForIds(Duration.ofSeconds(3), List.of())
        .expectError(InternalServerError.class)
        .verify();
  }

  @Test
  void firstRequestToCohortFailsInAllSelector() throws IOException {
    var idPrefix = "patient-917653";
    int total = 3;

    allCohortSelector.consentForNPatients(idPrefix, total, List.of(500, 200));
    prepareCohortWithPaging(idPrefix, total);

    startProcess(Duration.ofSeconds(12))
        .assertNext(r -> completedWithBundles(total, r))
        .verifyComplete();
  }

  @Test
  void firstRequestToCohortFailsInListSelector() throws IOException {
    var idPrefix = "patient-241352";
    int total = 3;

    listCohortSelector.consentForNPatients(idPrefix, total, List.of(500, 200));
    var ids = prepareCohortWithPaging(idPrefix, total);

    startProcessForIds(Duration.ofSeconds(12), ids)
        .assertNext(r -> completedWithBundles(total, r))
        .verifyComplete();
  }

  @Test
  void someRequestsToCohortFailDuringPagingInAllSelector() throws IOException {
    var idPrefix = "patient-819305";
    int total = 7;
    int maxPageSize = 2;

    allCohortSelector.consentForNPatientsWithPaging(
        idPrefix, total, maxPageSize, List.of(200, 500, 500, 200, 200, 500, 200));
    prepareCohortWithPaging(idPrefix, total);

    startProcess(Duration.ofSeconds(12))
        .assertNext(r -> completedWithBundles(total, r))
        .verifyComplete();
  }

  @Test
  void someRequestsToCohortFailDuringPagingInListSelector() throws IOException {
    var idPrefix = "patient-101183";
    int total = 7;
    int maxPageSize = 2;

    listCohortSelector.consentForNPatientsWithPaging(
        idPrefix, total, maxPageSize, List.of(200, 500, 500, 200, 200, 500, 200));
    var ids = prepareCohortWithPaging(idPrefix, total);

    startProcessForIds(Duration.ofSeconds(12), ids)
        .assertNext(r -> completedWithBundles(total, r))
        .verifyComplete();
  }

  private List<String> prepareCohortWithPaging(String idPrefix, int total) throws IOException {
    var patientsAndIds = generateNPatients(idPrefix, "2025", DEFAULT_IDENTIFIER_SYSTEM, total);
    var patients = patientsAndIds.bundle();
    var ids = patientsAndIds.ids();

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
    return ids;
  }
}
