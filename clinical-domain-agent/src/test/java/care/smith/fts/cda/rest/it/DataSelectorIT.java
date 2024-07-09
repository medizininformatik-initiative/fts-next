package care.smith.fts.cda.rest.it;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.State;
import care.smith.fts.cda.TransferProcessRunner.Status;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class DataSelectorIT extends TransferProcessControllerIT {
  private static final String patientId = "patientId";

  @Test
  void hdsDown() throws IOException {
    mockCohortSelector.successOnePatient(patientId);
    mockDataSelector.getMockFhirResolveService().success(patientId, DEFAULT_IDENTIFIER_SYSTEM);
    mockDataSelector.getMockFetchData().isDown(patientId);
    startProcess(1);
  }

  @Test
  void hdsTimeout() throws IOException {
    mockCohortSelector.successOnePatient(patientId);
    mockDataSelector.getMockFhirResolveService().success(patientId, DEFAULT_IDENTIFIER_SYSTEM);
    mockDataSelector.getMockFetchData().timeout(patientId);
    startProcess(11);
  }

  @Test
  void hdsReturnsWrongContentType() throws IOException {
    mockCohortSelector.successOnePatient(patientId);
    mockDataSelector.getMockFhirResolveService().success(patientId, DEFAULT_IDENTIFIER_SYSTEM);
    mockDataSelector.getMockFetchData().wrongContentType(patientId);
    startProcess(1);
  }

  @Test
  void hdsReturnsEmptyBundle() throws IOException {
    mockCohortSelector.successOnePatient(patientId);
    mockDataSelector.getMockFhirResolveService().success(patientId, DEFAULT_IDENTIFIER_SYSTEM);
    mockDataSelector.getMockFetchData().emptyBundle(patientId);
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
