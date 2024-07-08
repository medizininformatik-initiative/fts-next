package care.smith.fts.cda.rest.it;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.State;
import care.smith.fts.cda.TransferProcessRunner.Status;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class DataSelectorIT extends TransferProcessControllerIT {

  @Test
  void hdsDown() throws IOException {
    String patientId = randomUUID().toString();

    mockCohortSelector.successOnePatient(patientId);
    mockDataSelector.getMockFhirResolveService().isDown();

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
                          assertThat(r.patientsSkippedCount()).isEqualTo(1);
                          assertThat(r.status()).isEqualTo(Status.COMPLETED);
                        })
                    .verifyComplete());
  }

  @Test
  void hdsTimeout() throws IOException {
    String patientId = randomUUID().toString();

    mockCohortSelector.successOnePatient(patientId);
    mockDataSelector.getMockFhirResolveService().timeout();
    client
        .post()
        .uri("/api/v2/process/test/start")
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .flatMap(
            r ->
                Mono.delay(Duration.ofSeconds(11))
                    .flatMap(
                        i -> client.get().uri(r.getFirst()).retrieve().bodyToMono(State.class)))
        .as(
            response ->
                StepVerifier.create(response)
                    .assertNext(
                        r -> {
                          assertThat(r.patientsSkippedCount()).isEqualTo(1);
                          assertThat(r.status()).isEqualTo(Status.COMPLETED);
                        })
                    .verifyComplete());
  }
}
