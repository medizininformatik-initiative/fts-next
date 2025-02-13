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

  private static final String PROCESS_ID = "processId";
  private static final TransferProcessStatus PATIENT_SUMMARY_RESULT =
      TransferProcessStatus.create(PROCESS_ID);
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
                if (processId.equals(PROCESS_ID)) {
                  return Mono.just(PATIENT_SUMMARY_RESULT);
                } else {
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
            .path("api/v2/process/status/processId")
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
  void status() {
    create(api.status(PROCESS_ID))
        .expectNext(
            ResponseEntity.accepted()
                .headers(h -> h.add(X_PROGRESS, "Queued"))
                .body(PATIENT_SUMMARY_RESULT))
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
        .expectNext(ResponseEntity.ok(List.of(PATIENT_SUMMARY_RESULT)))
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
