package care.smith.fts.cda.rest.it;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.State;
import care.smith.fts.cda.TransferProcessRunner.Status;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class FhirResolveServiceIT extends TransferProcessControllerIT {
  private static final String patientId = "id1";

  @BeforeEach
  void setUp() throws IOException {
    mockCohortSelector.successOnePatient(patientId);
  }

  @Test
  void hdsDown() {
    mockDataSelector.getMockFhirResolveService().isDown(patientId, DEFAULT_IDENTIFIER_SYSTEM);
    startProcess(1);
  }

  @Test
  void hdsTimeout() {
    mockDataSelector.getMockFhirResolveService().timeout(patientId, DEFAULT_IDENTIFIER_SYSTEM);
    startProcess(11);
  }

  @Test
  void hdsReturnsWrongContentType() {
    mockDataSelector
        .getMockFhirResolveService()
        .wrongContentType(patientId, DEFAULT_IDENTIFIER_SYSTEM);
    startProcess(1);
  }

  @Test
  void hdsReturnsMoreThanOneResult() throws IOException {
    mockDataSelector
        .getMockFhirResolveService()
        .moreThanOneResult(patientId, DEFAULT_IDENTIFIER_SYSTEM);
    startProcess(1);
  }

  @Test
  void hdsReturnsEmptyBundle() {
    mockDataSelector.getMockFhirResolveService().emptyBundle(patientId, DEFAULT_IDENTIFIER_SYSTEM);
    startProcess(1);
  }

  private void startProcess(int seconds) {
    client
        .post()
        .uri("/api/v2/process/test/start")
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .flatMap(
            r ->
                Mono.delay(Duration.ofSeconds(seconds))
                    .flatMap(
                        i -> client.get().uri(r.getFirst()).retrieve().bodyToMono(State.class)))
        .as(
            response ->
                StepVerifier.create(response)
                    .assertNext(
                        r -> {
                          assertThat(r.status()).isEqualTo(Status.COMPLETED);
                          assertThat(r.patientsSkippedCount()).isEqualTo(1);
                        })
                    .verifyComplete());
  }
}
