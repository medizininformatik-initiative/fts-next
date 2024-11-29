package care.smith.fts.cda.rest;

import static java.util.List.of;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static reactor.test.StepVerifier.create;

import care.smith.fts.cda.TransferProcessConfig;
import care.smith.fts.cda.TransferProcessDefinition;
import care.smith.fts.cda.TransferProcessRunner;
import care.smith.fts.cda.TransferProcessRunner.Phase;
import care.smith.fts.cda.TransferProcessStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

class TransferProcessControllerTest {

  private static final String processId = "processId";
  private static final TransferProcessStatus PATIENT_SUMMARY_RESULT =
      TransferProcessStatus.create(processId);
  private TransferProcessController api;

  @BeforeEach
  void setUp() {
    PATIENT_SUMMARY_RESULT.setPhase(Phase.RUNNING);
    api =
        new TransferProcessController(
            new TransferProcessRunner() {
              @Override
              public String start(TransferProcessDefinition process, List<String> pids) {
                return "processId";
              }

              @Override
              public Mono<List<TransferProcessStatus>> statuses() {
                return Mono.just(List.of(PATIENT_SUMMARY_RESULT));
              }

              @Override
              public Mono<TransferProcessStatus> status(String processId) {
                return Mono.just(PATIENT_SUMMARY_RESULT);
              }
            },
            of(mockTransferProcess()));
  }

  @Test
  void startExistingProjectSucceeds() {
    var start =
        api.start(
            "example", UriComponentsBuilder.fromUriString("http://localhost:1234"), List.of());
    var uri =
        UriComponentsBuilder.fromUriString("http://localhost:1234")
            .path("api/v2/process/status/processId")
            .build()
            .toUri();
    create(start)
        .expectNext(
            ResponseEntity.accepted()
                .headers(h -> h.add("Content-Location", uri.toString()))
                .build())
        .verifyComplete();
  }

  @Test
  void startNonExistingProjectErrors() {
    var start =
        api.start(
            "non-existent", UriComponentsBuilder.fromUriString("http://localhost:1234"), List.of());
    create(start)
        .expectNext(
            ResponseEntity.of(
                    ProblemDetail.forStatusAndDetail(
                        NOT_FOUND, "Project 'non-existent' could not be found"))
                .build())
        .verifyComplete();
  }

  private static TransferProcessDefinition mockTransferProcess() {
    return new TransferProcessDefinition(
        "example",
        new TransferProcessConfig(null, null, null, null),
        pids -> null,
        consentedPatient -> null,
        patientBundle -> null,
        transportBundle -> null);
  }
}
