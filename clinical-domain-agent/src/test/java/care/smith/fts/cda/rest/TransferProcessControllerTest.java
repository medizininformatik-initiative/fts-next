package care.smith.fts.cda.rest;

import static care.smith.fts.util.HeaderTypes.X_PROGRESS;
import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static reactor.test.StepVerifier.create;

import care.smith.fts.cda.TransferProcessConfig;
import care.smith.fts.cda.TransferProcessDefinition;
import care.smith.fts.cda.TransferProcessRunner;
import care.smith.fts.cda.TransferProcessRunner.Phase;
import care.smith.fts.cda.TransferProcessStatus;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Slf4j
class TransferProcessControllerTest {

  private static final String QUEUED_PROCESS_ID = "queuedProcessId";
  private static final TransferProcessStatus QUEUED_PATIENT_SUMMARY_RESULT =
      TransferProcessStatus.create(QUEUED_PROCESS_ID);
  private static final String RUNNING_PROCESS_ID = "runningProcessId";
  private static final TransferProcessStatus RUNNING_PATIENT_SUMMARY_RESULT =
      TransferProcessStatus.create(RUNNING_PROCESS_ID).setPhase(Phase.RUNNING);
  private static final String COMPLETED_PROCESS_ID = "completedProcessId";
  private static final TransferProcessStatus COMPLETED_PATIENT_SUMMARY_RESULT =
      TransferProcessStatus.create(COMPLETED_PROCESS_ID).setPhase(Phase.COMPLETED);
  private static final String COMPLETED_WITH_ERROR_PROCESS_ID = "completedWithErrorProcessId";
  private static final TransferProcessStatus COMPLETED_WITH_ERROR_PATIENT_SUMMARY_RESULT =
      TransferProcessStatus.create(COMPLETED_WITH_ERROR_PROCESS_ID)
          .setPhase(Phase.COMPLETED_WITH_ERROR);
  private static final String FATAL_PROCESS_ID = "fatalProcessId";
  private static final TransferProcessStatus FATAL_PATIENT_SUMMARY_RESULT =
      TransferProcessStatus.create(FATAL_PROCESS_ID).setPhase(Phase.FATAL);

  private TransferProcessController api;

  @BeforeEach
  void setUp() {
    QUEUED_PATIENT_SUMMARY_RESULT.setPhase(Phase.RUNNING);
    api =
        new TransferProcessController(
            new TransferProcessRunner() {
              @Override
              public String start(TransferProcessDefinition process, List<String> pids) {
                return QUEUED_PROCESS_ID;
              }

              @Override
              public Mono<List<TransferProcessStatus>> statuses() {
                return Mono.just(List.of(QUEUED_PATIENT_SUMMARY_RESULT));
              }

              @Override
              public Mono<TransferProcessStatus> status(String processId) {
                switch (processId) {
                  case QUEUED_PROCESS_ID:
                    return Mono.just(QUEUED_PATIENT_SUMMARY_RESULT);
                  case RUNNING_PROCESS_ID:
                    return Mono.just(RUNNING_PATIENT_SUMMARY_RESULT);
                  case COMPLETED_PROCESS_ID:
                    return Mono.just(COMPLETED_PATIENT_SUMMARY_RESULT);
                  case COMPLETED_WITH_ERROR_PROCESS_ID:
                    return Mono.just(COMPLETED_WITH_ERROR_PATIENT_SUMMARY_RESULT);
                  case FATAL_PROCESS_ID:
                    return Mono.just(FATAL_PATIENT_SUMMARY_RESULT);
                  default:
                    return Mono.error(
                        new IllegalStateException(
                            "No transfer process with processId: " + processId));
                }
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
            .path("api/v2/process/status/queuedProcessId")
            .build()
            .toUri();
    create(start)
        .expectNext(
            ResponseEntity.accepted().headers(h -> h.add(CONTENT_LOCATION, uri.toString())).build())
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

  @Test
  void queuedStatus() {
    create(api.status("queuedProcessId"))
        .expectNext(
            ResponseEntity.accepted()
                .headers(h -> h.add(X_PROGRESS, "Queued"))
                .body(QUEUED_PATIENT_SUMMARY_RESULT))
        .verifyComplete();
  }

  @Test
  void runningStatus() {
    create(api.status(RUNNING_PROCESS_ID))
        .expectNext(
            ResponseEntity.accepted()
                .headers(h -> h.add(X_PROGRESS, "Running"))
                .body(RUNNING_PATIENT_SUMMARY_RESULT))
        .verifyComplete();
  }

  @Test
  void completedStatus() {
    create(api.status(COMPLETED_PROCESS_ID))
        .expectNext(ResponseEntity.ok().body(COMPLETED_PATIENT_SUMMARY_RESULT))
        .verifyComplete();
  }

  @Test
  void completedWWithErrorStatus() {
    create(api.status(COMPLETED_WITH_ERROR_PROCESS_ID))
        .expectNext(ResponseEntity.ok().body(COMPLETED_WITH_ERROR_PATIENT_SUMMARY_RESULT))
        .verifyComplete();
  }

  @Test
  void fatalStatus() {
    create(api.status(FATAL_PROCESS_ID))
        .expectNext(ResponseEntity.internalServerError().body(FATAL_PATIENT_SUMMARY_RESULT))
        .verifyComplete();
  }

  @Test
  void statusWithUnknownProcessIdReturns404() {
    create(api.status("unknown"))
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(NOT_FOUND))
        .verifyComplete();
  }

  @Test
  void statuses() {
    create(api.statuses())
        .expectNext(ResponseEntity.ok(List.of(QUEUED_PATIENT_SUMMARY_RESULT)))
        .verifyComplete();
  }

  @Test
  void projects() {
    assertThat(api.projects()).isEqualTo(ResponseEntity.ok(List.of("example")));
  }

  @Test
  void project() {
    var config = api.project("example");
    assertThat(config.getStatusCode()).isEqualTo(OK);
  }

  @Test
  void unknownProject() {
    var config = api.project("unknown");
    assertThat(config.getStatusCode()).isEqualTo(NOT_FOUND);
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
