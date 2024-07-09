package care.smith.fts.cda.rest.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.r4.model.ResourceType.Bundle;

import care.smith.fts.cda.TransferProcessRunner.State;
import care.smith.fts.test.TestPatientGenerator;
import java.io.IOException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Slf4j
public class GeneralIT extends TransferProcessControllerIT {

  @Test
  void successfulRequest() throws IOException {

    String patientId = "patientId";
    var patientsAndIds =
        TestPatientGenerator.generateNPatients(patientId, "2025", DEFAULT_IDENTIFIER_SYSTEM, 3);
    var patients = patientsAndIds.bundle();
    var ids = patientsAndIds.ids();

    mockCohortSelector.successNPatients(patientId, 3);
    for (var i = 0; i < patients.getTotal(); i++) {
      var id = ids.get(i);
      mockDataSelector.getMockTransportIds().success(om, id, DEFAULT_IDENTIFIER_SYSTEM);
      mockDataSelector.getMockFhirResolveService().success(id, DEFAULT_IDENTIFIER_SYSTEM);
      mockDataSelector
          .getMockFetchData()
          .success(id, new Bundle().addEntry(patients.getEntry().get(i)));
    }

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
        .assertNext(r -> assertThat(r.bundlesSentCount()).isEqualTo(3))
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
