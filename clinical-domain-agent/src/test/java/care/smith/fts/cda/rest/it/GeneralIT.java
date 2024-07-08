package care.smith.fts.cda.rest.it;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.State;
import care.smith.fts.test.TestPatientGenerator;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class GeneralIT extends TransferProcessControllerIT {

  @Test
  void successfulRequest() throws IOException {

    String patientId = randomUUID().toString();
    var patient =
        TestPatientGenerator.generateOnePatient(patientId, "2025", DEFAULT_IDENTIFIER_SYSTEM);

    mockCohortSelector.successOnePatient(patientId);
    mockDataSelector.getMockTransportIds().success(om, patientId, DEFAULT_IDENTIFIER_SYSTEM);
    mockDataSelector.getMockFhirResolveService().success(patientId, DEFAULT_IDENTIFIER_SYSTEM);
    mockDataSelector.getMockFetchData().success(patientId, patient);

    mockBundleSender.success();

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
}
