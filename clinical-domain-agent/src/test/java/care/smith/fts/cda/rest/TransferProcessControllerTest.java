package care.smith.fts.cda.rest;

import static java.util.List.of;
import static reactor.test.StepVerifier.create;

import care.smith.fts.cda.TransferProcess;
import care.smith.fts.cda.TransferProcessRunner;
import care.smith.fts.cda.TransferProcessRunner.State;
import care.smith.fts.cda.TransferProcessRunner.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

class TransferProcessControllerTest {

  private static final String processId = "processId";
  private static final State PATIENT_SUMMARY_RESULT = new State(processId, Status.RUNNING, 0, 0);
  private TransferProcessController api;

  @BeforeEach
  void setUp() {
    api =
        new TransferProcessController(
            new TransferProcessRunner() {
              @Override
              public String run(TransferProcess process) {
                return "processId";
              }

              @Override
              public Mono<State> state(String processId) {
                return Mono.just(PATIENT_SUMMARY_RESULT);
              }
            },
            of(mockTransferProcess()));
  }

  @Test
  void startExistingProjectSucceeds() {
    var start = api.start("example", UriComponentsBuilder.fromUriString("http://localhost:1234"));
    var uri =
        UriComponentsBuilder.fromUriString("http://localhost:1234")
            .path("api/v2/process/status/processId")
            .build()
            .toUri();
    create(start)
        .expectNext(
            ResponseEntity.accepted()
                .headers(h -> h.add("Content-Location", uri.toString()))
                .build()) // body(new State("processId", Status.RUNNING, 0, 0)))
        .verifyComplete();
  }

  @Test
  void startNonExistingProjectErrors() {
    var start =
        api.start("non-existent", UriComponentsBuilder.fromUriString("http://localhost:1234"));
    create(start)
        .expectNext(
            ResponseEntity.of(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Project non-existent could not be found"))
                .build())
        .verifyComplete();
  }

  private static TransferProcess mockTransferProcess() {
    return new TransferProcess(
        "example",
        () -> null,
        consentedPatient -> null,
        (patientBundle) -> null,
        (transportBundle) -> null);
  }
}
