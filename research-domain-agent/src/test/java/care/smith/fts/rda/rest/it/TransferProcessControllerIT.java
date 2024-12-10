package care.smith.fts.rda.rest.it;

import static care.smith.fts.rda.TransferProcessRunner.Phase.RUNNING;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import care.smith.fts.rda.ResearchDomainAgent;
import care.smith.fts.rda.TransferProcessRunner.Phase;
import care.smith.fts.rda.TransferProcessRunner.Status;
import care.smith.fts.rda.rest.it.mock.MockBundleSender;
import care.smith.fts.rda.rest.it.mock.MockDeidentifier;
import care.smith.fts.test.TestWebClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
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

/* RDA IT
 * - [x] patient endpoint
 *   - [x] invalid project
 *   - [x] wrong content type
 *   - [x] Body not a bundle/cannot deserialize
 *   - [x] Deidentifier
 *     - [x] TCA timeout
 *     - [x] TCA down
 *     - [x] Wrong content type
 *   - [x] BundleSender
 *     - [x] HDS slow
 *     - [x] HDS down
 * - [x] status endpoint
 *   - [x] unknown processId
 */

@Slf4j
@SpringBootTest(classes = ResearchDomainAgent.class, webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
public class TransferProcessControllerIT extends BaseIT {
  protected WebClient client;
  protected int port;

  protected final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

  protected final MockDeidentifier mockDeidentifier = new MockDeidentifier(om, tca);
  protected final MockBundleSender mockBundleSender = new MockBundleSender(hds);


  @BeforeEach
  void setUp(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    this.port = port;
    client = factory.webClient("https://localhost:" + port);
  }

  @AfterEach
  void tearDown() {
    resetAll();
  }

  protected FirstStep<Status> startProcess(Duration timeout, Bundle bundle) {
    return startProcess(timeout, bundle, s -> s.phase() != RUNNING);
  }

  protected FirstStep<Status> startProcess(Duration timeout, Bundle bundle, Predicate<Status> until) {
    return client
        .post()
        .uri("/api/v2/process/test/patient")
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .bodyValue(bundle)
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .flatMapMany(r -> Flux.interval(Duration.ofMillis(0), Duration.ofMillis(500))
            .flatMap(i -> retrieveStatus(r))
            .takeUntil(until)
            .take(timeout)
            .timeout(timeout, retrieveStatus(r))
        )
        .last()
        .as(StepVerifier::create);
  }

  private Mono<Status> retrieveStatus(List<String> r) {
    return client.get().uri(r.getFirst()).retrieve().bodyToMono(Status.class);
  }

  protected static void completeWithResources(Status r, int received, int sent) {
    assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
    assertThat(r.receivedResources()).isEqualTo(received);
    assertThat(r.sentResources()).isEqualTo(sent);
  }
}
