package care.smith.fts.cda.rest.it;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.State;
import care.smith.fts.cda.TransferProcessRunner.Status;
import java.io.IOException;
import java.time.Duration;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class BundleSenderIT extends TransferProcessControllerIT {
  private static final String patientId = "patientId";
  private static Bundle patient;

  public BundleSenderIT() throws IOException {
    patient = generateOnePatient(patientId, "2025", DEFAULT_IDENTIFIER_SYSTEM);
  }

  @BeforeEach
  void setUp() throws IOException {
    mockCohortSelector.successOnePatient(patientId);
    mockDataSelector.getMockFhirResolveService().success(patientId, DEFAULT_IDENTIFIER_SYSTEM);
    mockDataSelector.getMockFetchData().success(patientId, patient);
    mockDataSelector.getMockTransportIds().success(om, patientId, DEFAULT_IDENTIFIER_SYSTEM);
  }

  @Test
  void hdsDown() {
    mockBundleSender.isDown();
    startProcess(1);
  }

  @Test
  void hdsTimeout() {
    mockBundleSender.timeout();
    startProcess(12);
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
                          assertThat(r.bundlesSentCount()).isEqualTo(0);
                          assertThat(r.patientsSkippedCount()).isEqualTo(1);
                        })
                    .verifyComplete());
  }
}
