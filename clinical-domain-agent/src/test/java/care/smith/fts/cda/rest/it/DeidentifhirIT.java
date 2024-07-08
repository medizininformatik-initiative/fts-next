package care.smith.fts.cda.rest.it;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.State;
import care.smith.fts.cda.TransferProcessRunner.Status;
import care.smith.fts.test.TestPatientGenerator;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class DeidentifhirIT extends TransferProcessControllerIT {

  @Disabled
  @Test
  void deidentifhirUnknownDomain() throws IOException {
    String patientId = randomUUID().toString();
    var patient =
        TestPatientGenerator.generateOnePatient(patientId, "2025", DEFAULT_IDENTIFIER_SYSTEM);
    mockCohortSelector.successOnePatient(patientId);
    mockDataSelector.getMockFhirResolveService().success(patientId, DEFAULT_IDENTIFIER_SYSTEM);
    mockDataSelector.getMockFetchData().success(patientId, patient);

    mockDataSelector.getMockTransportIds().unknownDomain(om);

    client
        .post()
        .uri("/api/v2/process/test/start")
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .flatMap(
            r ->
                Mono.delay(Duration.ofSeconds(3))
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
