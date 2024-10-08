package care.smith.fts.cda.rest.it;

import static care.smith.fts.cda.TransferProcessRunner.Phase.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import care.smith.fts.cda.ClinicalDomainAgent;
import care.smith.fts.cda.TransferProcessRunner.Phase;
import care.smith.fts.cda.TransferProcessRunner.Status;
import care.smith.fts.cda.rest.it.mock.MockBundleSender;
import care.smith.fts.cda.rest.it.mock.MockCohortSelector;
import care.smith.fts.cda.rest.it.mock.MockDataSelector;
import care.smith.fts.test.TestWebClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.StepVerifier.FirstStep;

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
 * - [x] CohortSelector
 *   - [x] TCA slow
 *   - [x] TCA down
 *   - [x] Wrong content type
 *   - [x] TCA/gICS: unknown domain, this is the only setting that returns an error (bad request),
 * other settings may return an empty bundle
 * - [x] DataSelector
 *   - [x] FhirResolveService
 *     - [x] HDS slow
 *     - [x] HDS down
 *     - [x] wrong content type
 *     - [x] may return error:
 *       - [x] More than one result
 *       - [x] Unable to resolve patient id
 *   - [x] everything
 *     - [x] HDS slow
 *     - [x] HDS down
 *     - [x] wrong content type
 *     - [x] paging
 * - [x] Deidentifhir
 *   - [x] TCA slow
 *   - [x] TCA down
 *   - [x] gPAS unknown domain -> bad request
 * - [x] BundleSender
 *   - [x] RDA slow
 *   - [x] RDA down
 */
@Slf4j
@SpringBootTest(classes = ClinicalDomainAgent.class, webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
public class TransferProcessControllerIT extends BaseIT {
  protected WebClient client;

  protected final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

  protected final MockCohortSelector mockCohortSelector = new MockCohortSelector(tca);
  protected final MockDataSelector mockDataSelector = new MockDataSelector(om, tca, hds);
  protected final MockBundleSender mockBundleSender = new MockBundleSender(rda);

  protected static final String DEFAULT_IDENTIFIER_SYSTEM = "http://fts.smith.care";

  @BeforeEach
  void setUp(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    client = factory.webClient().baseUrl("https://localhost:" + port).build();
  }

  @AfterEach
  void tearDown() {
    resetAll();
  }



  protected FirstStep<Status> startProcess(Duration timeout) {
    return startProcess(timeout, s -> s.phase() != RUNNING);
  }

  protected FirstStep<Status> startProcess(Duration timeout, Predicate<Status> until) {
    return client
        .post()
        .uri("/api/v2/process/test/start")
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .doOnNext(r -> assertThat(r).isNotEmpty())
        .doOnNext(r -> assertThat(r.getFirst()).contains("/api/v2/process/status/"))
        .flatMapMany(
            r ->
                Flux.interval(Duration.ofMillis(0), Duration.ofMillis(500))
                    .flatMap(i -> retrieveStatus(r))
                    .takeUntil(until)
                    .take(timeout))
        .last()
        .as(StepVerifier::create);
  }

  protected FirstStep<Status> startProcessForIds(Duration timeout, List<String> ids) {
    return startProcessForIds(timeout, s -> s.phase() != RUNNING, ids);
  }

  protected FirstStep<Status> startProcessForIds(
      Duration timeout, Predicate<Status> until, List<String> ids) {
    return client
        .post()
        .uri("/api/v2/process/test/start")
        .bodyValue(ids)
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .doOnNext(r -> assertThat(r).isNotEmpty())
        .doOnNext(r -> assertThat(r.getFirst()).contains("/api/v2/process/status/"))
        .flatMapMany(
            r ->
                Flux.interval(Duration.ofMillis(0), Duration.ofMillis(500))
                    .flatMap(i -> retrieveStatus(r))
                    .takeUntil(until)
                    .take(timeout))
        .last()
        .as(StepVerifier::create);
  }

  private Mono<Status> retrieveStatus(List<String> r) {
    return client.get().uri(r.getFirst()).retrieve().bodyToMono(Status.class);
  }

  protected static void completedWithBundles(int expectedBundlesSent, Status r) {
    expectPhase(r, Phase.COMPLETED);
    assertThat(r.bundlesSentCount()).isEqualTo(expectedBundlesSent);
  }

  protected static void errored(Status r) {
    expectPhase(r, Phase.ERROR);
    assertThat(r.bundlesSkippedCount()).isEqualTo(1);
  }

  protected static void expectPhase(Status r, Phase phase) {
    assertThat(r.phase()).isEqualTo(phase);
  }
}
