package care.smith.fts.cda;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.*;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.BundleSender.Result;
import care.smith.fts.cda.TransferProcessRunner.Phase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DefaultTransferProcessRunnerTest {

  private static final String PATIENT_ID = "patient-150622";
  private static final ConsentedPatient PATIENT = new ConsentedPatient(PATIENT_ID, "system");

  private DefaultTransferProcessRunner runner;

  private final TransferProcessRunnerConfig config;
  private final TransferProcessConfig rawConfig = new TransferProcessConfig(null, null, null, null);

  DefaultTransferProcessRunnerTest() {
    config = new TransferProcessRunnerConfig();
    config.setMaxSendConcurrency(64);
    config.setMaxConcurrentProcesses(2);
    config.setProcessTtl(Duration.ofSeconds(3));
  }

  @BeforeEach
  void setUp() {
    runner = new DefaultTransferProcessRunner(new ObjectMapper(), config);
  }

  @Test
  void runMockTestSuccessfully() {
    var process =
        new TransferProcessDefinition(
            "test",
            rawConfig,
            pids -> fromIterable(List.of(PATIENT)),
            p -> fromIterable(List.of(new ConsentedPatientBundle(new Bundle(), PATIENT))),
            b -> just(new TransportBundle(new Bundle(), "transferId")),
            b -> just(new Result()));

    var processId = runner.start(process, List.of());
    create(runner.status(processId))
        .assertNext(
            r -> {
              assertThat(r.sentBundles()).isEqualTo(1);
              assertThat(r.skippedBundles()).isEqualTo(0);
            })
        .verifyComplete();
  }

  @Test
  void runMockTestWithSkippedBundles() {
    var first = new AtomicBoolean(true);
    var patient2 = new ConsentedPatient(PATIENT_ID, "system");
    var process =
        new TransferProcessDefinition(
            "test",
            rawConfig,
            pids -> fromIterable(List.of(PATIENT, patient2)),
            p -> fromIterable(List.of(new ConsentedPatientBundle(new Bundle(), PATIENT))),
            b -> just(new TransportBundle(new Bundle(), "transferId")),
            b -> {
              if (first.getAndSet(false)) {
                return just(new Result());
              } else {
                throw new RuntimeException("Cannot send bundle");
              }
            });

    var processId = runner.start(process, List.of());
    create(runner.status(processId))
        .assertNext(
            r -> {
              assertThat(r.sentBundles()).isEqualTo(1);
              assertThat(r.skippedBundles()).isEqualTo(1);
            })
        .verifyComplete();
  }

  @Test
  void errorInCohortSelector() {
    var process =
        new TransferProcessDefinition(
            "test",
            rawConfig,
            pids -> Flux.error(new Throwable("Error fetching consented patients")),
            p -> fromIterable(List.of(new ConsentedPatientBundle(new Bundle(), PATIENT))),
            b -> just(new TransportBundle(new Bundle(), "tIDMapName")),
            b -> Mono.just(new Result()));

    var processId = runner.start(process, List.of());
    create(runner.status(processId))
        .assertNext(
            r -> {
              assertThat(r.phase()).isEqualTo(Phase.FATAL);
              assertThat(r.sentBundles()).isEqualTo(0);
              assertThat(r.skippedBundles()).isEqualTo(0);
            })
        .verifyComplete();
  }

  @Test
  void startMultipleProcessesWithQueueing() {
    var process =
        new TransferProcessDefinition(
            "test",
            rawConfig,
            pids -> fromIterable(List.of(PATIENT)),
            p -> fromIterable(List.of(new ConsentedPatientBundle(new Bundle(), PATIENT))),
            b ->
                just(new TransportBundle(new Bundle(), "transferId"))
                    .delayElement(Duration.ofMillis(100)),
            b -> just(new Result()));

    var processId1 = runner.start(process, List.of());
    runner.start(process, List.of());
    var processId3 = runner.start(process, List.of());

    create(runner.status(processId3))
        .assertNext(r -> assertThat(r.phase()).isEqualTo(Phase.QUEUED))
        .verifyComplete();

    create(runner.statuses())
        .assertNext(
            r -> {
              assertThat(r.size()).isEqualTo(3);
              assertThat(r.stream().map(TransferProcessStatus::phase))
                  .containsExactlyInAnyOrder(Phase.RUNNING, Phase.RUNNING, Phase.QUEUED);
            })
        .verifyComplete();

    create(
            Flux.interval(Duration.ofMillis(50))
                .flatMap(i -> runner.status(processId1))
                .takeUntil(s -> TransferProcessStatus.isCompleted(s.phase()))
                .take(Duration.ofSeconds(10))
                .last())
        .expectNextCount(1)
        .verifyComplete();

    create(runner.statuses())
        .assertNext(
            r -> {
              assertThat(r.size()).isEqualTo(3);
              assertThat(r.stream().map(TransferProcessStatus::phase))
                  .containsExactlyInAnyOrder(Phase.RUNNING, Phase.COMPLETED, Phase.COMPLETED);
            })
        .verifyComplete();
  }

  @Test
  void ttl() throws InterruptedException {
    var process =
        new TransferProcessDefinition(
            "test",
            rawConfig,
            pids -> fromIterable(List.of(PATIENT)),
            p -> fromIterable(List.of(new ConsentedPatientBundle(new Bundle(), PATIENT))),
            b -> just(new TransportBundle(new Bundle(), "transferId")),
            b -> just(new Result()));
    config.setProcessTtl(Duration.ofMillis(100));
    var runner = new DefaultTransferProcessRunner(new ObjectMapper(), config);
    runner.start(process, List.of());
    runner.start(process, List.of());

    create(runner.statuses())
        .assertNext(
            r -> {
              assertThat(r.size()).isEqualTo(2);
            })
        .verifyComplete();

    sleep(110L); // wait TTL seconds for process 1 and 2 to be removed

    create(runner.statuses())
        .assertNext(
            r -> {
              assertThat(r.size()).isEqualTo(0);
            })
        .verifyComplete();
  }
}
