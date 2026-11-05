package care.smith.fts.cda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.api.CohortEntry.Excluded;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.BundleSender.Result;
import care.smith.fts.cda.TransferProcessRunner.Phase;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class VerifyPr2113Test {

  private static final ConsentedPatient PATIENT = new ConsentedPatient("p1", "system");
  private final TransferProcessConfig rawConfig = new TransferProcessConfig(null, null, null, null);

  private TransferProcessDefinition fastProcess() {
    return new TransferProcessDefinition(
        "test",
        rawConfig,
        pids -> Flux.just(new Consented(PATIENT)),
        p -> fromIterable(List.of(new ConsentedPatientBundle(new Bundle(), p))),
        b -> just(new TransportBundle(new Bundle(), "transferId")),
        b -> just(new Result()));
  }

  private static DefaultTransferProcessRunner runner(int maxProcesses) {
    var cfg = new TransferProcessRunnerConfig(64, 64, maxProcesses, 4, Duration.ofSeconds(10));
    return new DefaultTransferProcessRunner(new ObjectMapper(), cfg);
  }

  /** F1, direct path: a null identifier throws synchronously from start(). */
  @Test
  void f1NullIdentifierDirectPathDoesNotThrow() {
    var r = runner(2);
    assertThatNoException().isThrownBy(() -> r.start(fastProcess(), Arrays.asList("a", null)));
  }

  /** F1, queued path: the dequeued process with a null identifier must stay findable. */
  @Test
  void f1NullIdentifierQueuedPathProcessStaysFindable() {
    var r = runner(1);
    var gate = new CompletableFuture<Void>();
    var gated =
        new TransferProcessDefinition(
            "test",
            rawConfig,
            pids -> Flux.just(new Consented(PATIENT)),
            p ->
                Flux.just(new ConsentedPatientBundle(new Bundle(), p))
                    .delayUntil(v -> Mono.fromFuture(gate)),
            b -> just(new TransportBundle(new Bundle(), "transferId")),
            b -> just(new Result()));

    var firstId = r.start(gated, List.of());
    var queuedId = r.start(fastProcess(), Arrays.asList("a", null));
    create(r.status(queuedId))
        .assertNext(s -> assertThat(s.phase()).isEqualTo(Phase.QUEUED))
        .verifyComplete();

    gate.complete(null);
    waitForCompletion(r, firstId);

    create(r.status(firstId))
        .assertNext(s -> assertThat(s.phase()).as("first process").isEqualTo(Phase.COMPLETED))
        .verifyComplete();
    create(r.status(queuedId)).expectNextCount(1).verifyComplete();
  }

  /** F3: an Excluded entry seen before a cohort selection error must be reported. */
  @Test
  void f3ExcludedBeforeCohortErrorIsReported() {
    var r = runner(2);
    var process =
        new TransferProcessDefinition(
            "test",
            rawConfig,
            pids ->
                Flux.<care.smith.fts.api.CohortEntry>just(new Excluded("p2", "Faulty consent"))
                    .concatWith(Flux.error(new RuntimeException("page 2 boom"))),
            p -> fromIterable(List.of(new ConsentedPatientBundle(new Bundle(), p))),
            b -> just(new TransportBundle(new Bundle(), "transferId")),
            b -> just(new Result()));

    var id = r.start(process, List.of());
    waitForCompletion(r, id);

    create(r.status(id))
        .assertNext(s -> {
          assertThat(s.phase()).isEqualTo(Phase.FATAL);
          assertThat(s.excludedPatients()).isEqualTo(1);
        })
        .verifyComplete();
    create(r.failedPatients(id)).assertNext(e -> assertThat(e).hasSize(1)).verifyComplete();
  }

  /**
   * F6, consequence: for empty policies the TCA answers an empty bundle, so each requested patient
   * is reported as "No consent found". This test passes; it shows the misleading reason.
   */
  @Test
  void f6EmptySelectionReportsEachRequestedPatientAsNoConsentFound() {
    var report = new CohortReport(List.of("a", "b"));

    assertThat(report.excluded())
        .containsExactly(
            new Excluded("a", CohortReport.NO_CONSENT_FOUND),
            new Excluded("b", CohortReport.NO_CONSENT_FOUND));
  }

  private static void waitForCompletion(DefaultTransferProcessRunner r, String processId) {
    Flux.interval(Duration.ofMillis(10))
        .flatMap(i -> r.status(processId))
        .takeUntil(s -> TransferProcessStatus.isCompleted(s.phase()))
        .take(Duration.ofSeconds(5))
        .last()
        .as(StepVerifier::create)
        .expectNextCount(1)
        .verifyComplete();
  }
}
