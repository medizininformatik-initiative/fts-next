package care.smith.fts.cda.rest;

import static care.smith.fts.util.HeaderTypes.X_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;
import static reactor.test.StepVerifier.create;

import care.smith.fts.cda.TransferProcessConfig;
import care.smith.fts.cda.TransferProcessDefinition;
import care.smith.fts.cda.TransferProcessRunner;
import care.smith.fts.cda.TransferProcessRunner.Phase;
import care.smith.fts.cda.TransferProcessStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class TransferProcessControllerTest {

  private static final String EXISTING_PROJECT_NAME = "example";
  private static final String MOCK_HOST = "http://localhost:1234";

  @Mock TransferProcessRunner mockRunner;

  private TransferProcessController api;

  @BeforeEach
  void setUp() {
    var process =
        new TransferProcessDefinition(
            EXISTING_PROJECT_NAME,
            new TransferProcessConfig(null, null, null, null),
            pids -> null,
            consentedPatient -> null,
            patientBundle -> null,
            transportBundle -> null);
    api = new TransferProcessController(mockRunner, List.of(process));
  }

  @Test
  void startExistingProjectSucceeds() {
    var processId = "queued-094522";
    when(mockRunner.start(Mockito.any(TransferProcessDefinition.class), Mockito.anyList()))
        .thenReturn(processId);
    when(mockRunner.status(processId))
        .thenReturn(Mono.just(TransferProcessStatus.create(processId)));

    var start = api.start(EXISTING_PROJECT_NAME, fromUriString(MOCK_HOST), List.of());
    var uri = fromUriString(MOCK_HOST).path("api/v2/process/status/" + processId).build().toUri();
    create(start)
        .expectNext(
            ResponseEntity.accepted().headers(h -> h.add(CONTENT_LOCATION, uri.toString())).build())
        .verifyComplete();
  }

  @Test
  void startNonExistingProjectErrors() {
    var projectName = "non-existent";
    var result =
        ProblemDetail.forStatusAndDetail(
            NOT_FOUND, "Project '%s' could not be found".formatted(projectName));

    var start = api.start(projectName, fromUriString(MOCK_HOST), List.of());

    create(start).expectNext(ResponseEntity.of(result).build()).verifyComplete();
  }

  @Test
  void queuedStatus() {
    var processId = "queued-093021";
    var result = TransferProcessStatus.create(processId);

    when(mockRunner.status(processId)).thenReturn(Mono.just(result));

    create(api.status(processId))
        .expectNext(
            ResponseEntity.accepted().headers(h -> h.add(X_PROGRESS, "Queued")).body(result))
        .verifyComplete();
  }

  @Test
  void runningStatus() {
    var processId = "running-093129";
    var result = TransferProcessStatus.create(processId).setPhase(Phase.RUNNING);

    when(mockRunner.status(processId)).thenReturn(Mono.just(result));

    create(api.status(processId))
        .expectNext(
            ResponseEntity.accepted().headers(h -> h.add(X_PROGRESS, "Running")).body(result))
        .verifyComplete();
  }

  @Test
  void completedStatus() {
    var processId = "completed-093152";
    var result = TransferProcessStatus.create(processId).setPhase(Phase.COMPLETED);

    when(mockRunner.status(processId)).thenReturn(Mono.just(result));

    create(api.status(processId)).expectNext(ResponseEntity.ok().body(result)).verifyComplete();
  }

  @Test
  void completedWWithErrorStatus() {
    var processId = "w-errors-093201";
    var result = TransferProcessStatus.create(processId).setPhase(Phase.COMPLETED_WITH_ERROR);

    when(mockRunner.status(processId)).thenReturn(Mono.just(result));

    create(api.status(processId)).expectNext(ResponseEntity.ok().body(result)).verifyComplete();
  }

  @Test
  void fatalStatus() {
    var processId = "fatal-093249";
    var result = TransferProcessStatus.create(processId).setPhase(Phase.FATAL);

    when(mockRunner.status(processId)).thenReturn(Mono.just(result));

    create(api.status(processId))
        .expectNext(ResponseEntity.internalServerError().body(result))
        .verifyComplete();
  }

  @Test
  void statusWithUnknownProcessIdReturns404() {
    when(mockRunner.status(Mockito.anyString()))
        .thenReturn(Mono.error(new IllegalStateException("No transfer process with processId: ")));

    create(api.status("unknown"))
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(NOT_FOUND))
        .verifyComplete();
  }

  @Test
  void statuses() {
    var processId = "queued-094193";
    var result = TransferProcessStatus.create(processId);

    when(mockRunner.statuses()).thenReturn(Mono.just(List.of(result)));

    create(api.statuses()).expectNext(ResponseEntity.ok(List.of(result))).verifyComplete();
  }

  @Test
  void projects() {
    assertThat(api.projects()).isEqualTo(ResponseEntity.ok(List.of(EXISTING_PROJECT_NAME)));
  }

  @Test
  void project() {
    var config = api.project(EXISTING_PROJECT_NAME);
    assertThat(config.getStatusCode()).isEqualTo(OK);
  }

  @Test
  void unknownProject() {
    var config = api.project("unknown");
    assertThat(config.getStatusCode()).isEqualTo(NOT_FOUND);
  }
}
