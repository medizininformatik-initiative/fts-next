package care.smith.fts.rda.rest.it;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import care.smith.fts.rda.ResearchDomainAgent;
import care.smith.fts.rda.TransferProcessRunner.Phase;
import care.smith.fts.rda.TransferProcessRunner.Status;
import care.smith.fts.rda.rest.it.mock.MockBundleSender;
import care.smith.fts.rda.rest.it.mock.MockDeidentifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;
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
public class TransferProcessControllerIT extends BaseIT {
  protected WebClient client;

  protected final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

  protected final MockDeidentifier mockDeidentifier = new MockDeidentifier(om, tca);
  protected final MockBundleSender mockBundleSender = new MockBundleSender(hds);

  @BeforeEach
  void setUp(@LocalServerPort int port, @Autowired WebClient.Builder builder) {
    client = builder.baseUrl("http://localhost:" + port).build();
  }

  @AfterEach
  void tearDown() {
    resetAll();
  }

  protected FirstStep<Status> startProcess(Duration duration) {
    return startProcess(duration, new Bundle());
  }

  protected FirstStep<Status> startProcess(Duration duration, Bundle bundle) {
    return client
        .post()
        .uri("/api/v2/process/test/patient")
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .bodyValue(bundle)
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .flatMap(r -> Mono.delay(duration).flatMap(i -> retrieveStatus(r)))
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
