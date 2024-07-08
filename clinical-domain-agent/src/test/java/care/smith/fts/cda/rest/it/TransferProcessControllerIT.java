package care.smith.fts.cda.rest.it;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import care.smith.fts.cda.BaseIT;
import care.smith.fts.cda.ClinicalDomainAgent;
import care.smith.fts.cda.TransferProcessRunner.State;
import care.smith.fts.cda.TransferProcessRunner.Status;
import care.smith.fts.test.TestPatientGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/*
 * CDA has two endpoints to test: `/{project}/start` and `/status/{projectId}`.
 * The URL of the latter is part of the response of the first.
 *
 * The `start` endpoint does:
 * 1. Request ConsentedPatients from TCA
 *   a. Filter ConsentedPatients by date range
 * 2. Request FhirId from HDS
 * 3. Request everything from HDS
 * 4. Request transport ids from TCA
 *   a. Deidentify Patient
 * 5. Send Patient to RDA
 *
 * Things that can go wrong:
 * - [x] Invalid project
 * - [ ] CohortSelector
 *   - [x] TCA slow
 *   - [x] or down
 *   - [ ] Wrong content type
 *   - [ ] TCA/gICS: unknown domain, this is the only setting that returns an error (bad request),
 * other settings may return an empty bundle
 * - [ ] DataSelector
 *   - [ ] HDS slow or down
 *   - [ ] FhirResolveService
 *     - [ ] wrong content type
 *     - [ ] may return error:
 *       - [ ] More than one result
 *       - [ ] Unable to resolve patient id
 *   - [ ] everything
 *     - [ ] wrong content type
 *     - [ ] paging - not implemented
 * - [ ] Deidentifhir
 *   - [ ] TCA slow or down
 *   - [ ] gPAS unknown domain -> bad request
 * - [ ] BundleSender
 *   - [ ] RDA slow or down
 */
@Slf4j
@SpringBootTest(classes = ClinicalDomainAgent.class, webEnvironment = RANDOM_PORT)
public class TransferProcessControllerIT extends BaseIT {
  private WebClient client;

  ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

  ITCohortSelector itCohortSelector = new ITCohortSelector(tca);
  ITDataSelector itDataSelector = new ITDataSelector(tca, hds);
  ITBundleSender itBundleSender = new ITBundleSender(rda);

  @BeforeEach
  void setUp(@LocalServerPort int port) {
    client = WebClient.builder().baseUrl("http://localhost:" + port).build();
  }

  @AfterEach
  void tearDown() {
    resetAll();
  }

  @Test
  void successfulRequest() throws IOException {

    String patientId = randomUUID().toString();
    var identifierSystem = "http://fts.smith.care";
    var patient = TestPatientGenerator.generateOnePatient(patientId, "2025", identifierSystem);

    itCohortSelector.success(patientId);
    itDataSelector.itTransportIds.success(om, patientId, identifierSystem);
    itDataSelector.itFhirResolveService.success(patientId, identifierSystem);
    itDataSelector.itFetchData.success(patientId, patient);

    itBundleSender.success();

    StepVerifier.create(
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
                        Mono.delay(Duration.ofSeconds(3))
                            .flatMap(
                                i ->
                                    client
                                        .get()
                                        .uri(r.getFirst())
                                        .retrieve()
                                        .bodyToMono(State.class))))
        .assertNext(r -> assertThat(r.bundlesSentCount()).isEqualTo(1))
        .verifyComplete();
  }

  @Test
  void invalidProject() {
    StepVerifier.create(
            client
                .post()
                .uri("/api/v2/process/non-existent/start")
                .retrieve()
                .onStatus(
                    r -> r.equals(HttpStatus.resolve(500)),
                    (c) ->
                        c.bodyToMono(ProblemDetail.class)
                            .flatMap(p -> Mono.error(new IllegalStateException(p.getDetail()))))
                .toBodilessEntity())
        .expectErrorMessage("Project non-existent could not be found")
        .verifyThenAssertThat()
        .hasOperatorErrors();
  }

  @Test
  void cohortSelectorTCADown() {
    itCohortSelector.isDown();

    client
        .post()
        .uri("/api/v2/process/test/start")
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .flatMap(
            r ->
                Mono.delay(Duration.ofSeconds(1))
                    .flatMap(
                        i -> client.get().uri(r.getFirst()).retrieve().bodyToMono(State.class)))
        .as(
            response ->
                StepVerifier.create(response)
                    .assertNext(
                        r -> {
                          assertThat(r.status()).isEqualTo(Status.ERROR);
                        })
                    .verifyComplete());
  }

  @Test
  void cohortSelectorTimeoutConsentedPatientsRequest() {
    itCohortSelector.timeoutResponse();
    client
        .post()
        .uri("/api/v2/process/test/start")
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .flatMap(
            r ->
                Mono.delay(Duration.ofSeconds(10))
                    .flatMap(
                        i -> client.get().uri(r.getFirst()).retrieve().bodyToMono(State.class)))
        .as(
            response ->
                StepVerifier.create(response)
                    .assertNext(
                        r -> {
                          assertThat(r.status()).isEqualTo(Status.ERROR);
                        })
                    .verifyComplete());
  }

  @Test
  void cohortSelectorSendsWrongContentType() throws IOException {
    itCohortSelector.wrongContentType();
    client
        .post()
        .uri("/api/v2/process/test/start")
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .flatMap(
            r ->
                Mono.delay(Duration.ofMillis(200))
                    .flatMap(
                        i -> client.get().uri(r.getFirst()).retrieve().bodyToMono(State.class)))
        .as(
            response ->
                StepVerifier.create(response)
                    .assertNext(
                        r -> {
                          assertThat(r.status()).isEqualTo(Status.ERROR);
                        })
                    .verifyComplete());
  }
}

// Wrong domain ret from gics
// {
//  "resourceType": "OperationOutcome",
//  "issue": [
//    {
//      "severity": "error",
//      "code": "processing",
//      "diagnostics": "No consents found for domain  'MII333'."
//    }
//  ]
// }
