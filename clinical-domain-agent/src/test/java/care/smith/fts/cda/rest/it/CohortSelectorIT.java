package care.smith.fts.cda.rest.it;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.State;
import care.smith.fts.cda.TransferProcessRunner.Status;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class CohortSelectorIT extends TransferProcessControllerIT {

  @Test
  void cohortSelectorTCADown() {
    mockCohortSelector.isDown();

    startProcess(Duration.ofSeconds(1));
  }

  @Test
  void cohortSelectorTimeoutConsentedPatientsRequest() {
    mockCohortSelector.timeout();
    startProcess(Duration.ofSeconds(10));
  }

  @Test
  void cohortSelectorSendsWrongContentType() throws IOException {
    mockCohortSelector.wrongContentType();
    startProcess(Duration.ofMillis(200));
  }

  @Test
  void cohortSelectorUnknownDomain() throws JsonProcessingException {
    mockCohortSelector.unknownDomain(om);
    startProcess(Duration.ofMillis(200));
  }

  private void startProcess(Duration duration) {
    client
        .post()
        .uri("/api/v2/process/test/start")
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .flatMap(
            r ->
                Mono.delay(duration)
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
