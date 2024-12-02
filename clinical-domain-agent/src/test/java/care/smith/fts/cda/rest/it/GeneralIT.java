package care.smith.fts.cda.rest.it;

import static care.smith.fts.test.TestPatientGenerator.generateNPatients;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import care.smith.fts.cda.TransferProcessStatus;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Slf4j
public class GeneralIT extends TransferProcessControllerIT {

  @Test
  void successfulRequest() throws IOException {

    var idPrefix = "patientId";
    int totalPatients = 3;
    var patientsAndIds =
        generateNPatients(idPrefix, "2025", DEFAULT_IDENTIFIER_SYSTEM, totalPatients);
    var patients = patientsAndIds.bundle();
    var ids = patientsAndIds.ids();

    mockCohortSelector.consentForNPatients(idPrefix, totalPatients);
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
        .assertNext(r -> completedWithBundles(totalPatients, r))
        .verifyComplete();
  }

  @Test
  void successfulRequestWithPaging() throws IOException {

    var totalPatients = 7;
    var pageSize = 2;
    var idPrefix = "patientId";
    var patientsAndIds =
        generateNPatients(idPrefix, "2025", DEFAULT_IDENTIFIER_SYSTEM, totalPatients);
    var patients = patientsAndIds.bundle();
    var ids = patientsAndIds.ids();

    mockCohortSelector.consentForNPatientsWithPaging(idPrefix, totalPatients, pageSize);
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
        .assertNext(r -> completedWithBundles(totalPatients, r))
        .verifyComplete();
  }

  @Test
  void successfulRequestWithRetryAfter() throws IOException {

    var totalPatients = 3;
    var idPrefix = "patientId";
    var patientsAndIds =
        generateNPatients(idPrefix, "2025", DEFAULT_IDENTIFIER_SYSTEM, totalPatients);
    var patients = patientsAndIds.bundle();
    var ids = patientsAndIds.ids();

    mockCohortSelector.consentForNPatients(idPrefix, totalPatients);
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

    mockBundleSender.successWithRetryAfter();

    startProcess(Duration.ofSeconds(16))
        .assertNext(r -> completedWithBundles(totalPatients, r))
        .verifyComplete();
  }

  @Test
  void startProcessWithInvalidProject() {
    StepVerifier.create(
            client
                .post()
                .uri("/api/v2/process/non-existent/start")
                .retrieve()
                .onStatus(
                    r -> r.equals(HttpStatus.resolve(404)),
                    (c) ->
                        c.bodyToMono(ProblemDetail.class)
                            .flatMap(p -> Mono.error(new IllegalStateException(p.getDetail()))))
                .toBodilessEntity())
        .expectErrorMessage("Project 'non-existent' could not be found")
        .verifyThenAssertThat()
        .hasOperatorErrors();
  }

  @Test
  void callingStatusWithWrongProcessIdReturns404() throws IOException {

    var idPrefix = "patientId";
    var patientsAndIds = generateNPatients(idPrefix, "2025", DEFAULT_IDENTIFIER_SYSTEM, 3);
    var patients = patientsAndIds.bundle();
    var ids = patientsAndIds.ids();

    mockCohortSelector.consentForNPatients(idPrefix, 3);
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

    mockBundleSender.successWithRetryAfter();

    client
        .post()
        .uri("/api/v2/process/test/start")
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .doOnNext(r -> assertThat(r).isNotEmpty())
        .doOnNext(r -> assertThat(r.getFirst()).contains("/api/v2/process/status/"))
        .flatMap(
            r -> {
              var uri = r.getFirst().concat("-unknown-process-id");
              return client.get().uri(uri).retrieve().bodyToMono(TransferProcessStatus.class);
            })
        .as(
            response ->
                StepVerifier.create(response)
                    .expectError(NotFound.class)
                    .verifyThenAssertThat()
                    .hasOperatorErrors());
  }

  @Test
  void statuses() throws IOException {
    var idPrefix = "patientId";
    var patientsAndIds = generateNPatients(idPrefix, "2025", DEFAULT_IDENTIFIER_SYSTEM, 3);
    var patients = patientsAndIds.bundle();
    var ids = patientsAndIds.ids();

    mockCohortSelector.consentForNPatients(idPrefix, 3);
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

    client
        .post()
        .uri("/api/v2/process/test/start")
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .doOnNext(r -> assertThat(r).isNotEmpty())
        .doOnNext(r -> assertThat(r.getFirst()).contains("/api/v2/process/status/"))
        .flatMap(
            r ->
                client
                    .get()
                    .uri("/api/v2/process/statuses")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<TransferProcessStatus>>() {}))
        .as(
            response ->
                StepVerifier.create(response)
                    .assertNext(r -> assertThat(r.size()).isGreaterThanOrEqualTo(1))
                    .verifyComplete());
  }

  @Test
  void projects() {
    StepVerifier.create(client.get().uri("/api/v2/projects").retrieve().bodyToMono(String.class))
        .assertNext(
            transferProcessDefinitions -> {
              assertThat(transferProcessDefinitions).isEqualTo("[\"test\"]");
            })
        .verifyComplete();
  }

  @Test
  void project() {
    StepVerifier.create(
            client.get().uri("/api/v2/projects/test").retrieve().bodyToMono(String.class))
        .assertNext(
            transferProcessDefinitions -> {
              assertThat(transferProcessDefinitions).contains("trustCenterAgent");
              assertThat(transferProcessDefinitions)
                  .contains(
                      "\"policies\":[\"IDAT_erheben\",\"IDAT_speichern_verarbeiten\",\"MDAT_erheben\",\"MDAT_speichern_verarbeiten\"]");
              assertThat(transferProcessDefinitions).contains("everything");
              assertThat(transferProcessDefinitions).contains("MII");
              assertThat(transferProcessDefinitions)
                  .contains("https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym");
              assertThat(transferProcessDefinitions).contains("researchDomainAgent");
            })
        .verifyComplete();
  }

  @Test
  void projectReturns404() {
    StepVerifier.create(
            client.get().uri("/api/v2/projects/doesNotExist").retrieve().bodyToMono(String.class))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error)
                  .asInstanceOf(type(WebClientResponseException.class))
                  .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(NOT_FOUND));
            });
  }
}
