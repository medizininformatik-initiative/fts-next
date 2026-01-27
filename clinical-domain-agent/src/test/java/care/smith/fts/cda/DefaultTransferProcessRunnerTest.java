package care.smith.fts.cda;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.*;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.api.cda.BundleSender.Result;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.cda.TransferProcessRunner.Phase;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultTransferProcessRunnerTest {

  private static final String PATIENT_IDENTIFIER = "patient-150622";
  private static final ConsentedPatient PATIENT =
      new ConsentedPatient(PATIENT_IDENTIFIER, "system");
  private static final String PATIENT_IDENTIFIER_2 = "patient-142391";
  private static final ConsentedPatient PATIENT_2 =
      new ConsentedPatient(PATIENT_IDENTIFIER_2, "system");

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
  void errorInCohortSelectorIsFatal() {
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

    create(runner.statuses()).assertNext(r -> assertThat(r.size()).isEqualTo(2)).verifyComplete();

    sleep(110L); // wait TTL seconds for process 1 and 2 to be removed

    create(runner.statuses())
        .assertNext(
            r -> {
              assertThat(r.size()).isEqualTo(0);
            })
        .verifyComplete();
  }

  @Test
  void errorInDataSelectorSkipsBundleAndContinues() {
    var process =
        new TransferProcessDefinition(
            "test",
            rawConfig,
            pids -> fromIterable(List.of(PATIENT, PATIENT_2)),
            errorOnSecond(new Bundle()),
            b -> just(new TransportBundle(new Bundle(), "transferId")),
            b -> just(new Result()));

    var processId = runner.start(process, List.of());
    waitForCompletion(processId);

    create(runner.status(processId)).assertNext(this::completedWithErrors).verifyComplete();
  }

  private static DataSelector errorOnSecond(Bundle bundle) {
    var first = new AtomicBoolean(true);
    return p ->
        first.getAndSet(false)
            ? Flux.error(new RuntimeException("Cannot select data"))
            : Flux.just(bundle).map(b -> new ConsentedPatientBundle(b, p));
  }

  private void completedWithErrors(TransferProcessStatus r) {
    assertThat(r.sentBundles()).isEqualTo(1);
    assertThat(r.skippedBundles()).isEqualTo(1);
    assertThat(r.phase()).isEqualTo(Phase.COMPLETED_WITH_ERROR);
  }

  @Test
  void errorInDeidentificatorSkipsBundleAndContinues() {
    var process =
        new TransferProcessDefinition(
            "test",
            rawConfig,
            pids -> fromIterable(List.of(PATIENT, PATIENT_2)),
            p -> fromIterable(List.of(new ConsentedPatientBundle(new Bundle(), p))),
            errorOnSecond(new TransportBundle(new Bundle(), "transferId")),
            b -> just(new Result()));

    var processId = runner.start(process, List.of());
    waitForCompletion(processId);

    create(runner.status(processId)).assertNext(this::completedWithErrors).verifyComplete();
  }

  private static Deidentificator errorOnSecond(TransportBundle bundle) {
    var first = new AtomicBoolean(true);
    return b ->
        first.getAndSet(false)
            ? just(bundle)
            : Mono.error(new RuntimeException("Cannot deidentify bundle"));
  }

  @Test
  void errorInBundleSenderSkipsBundleAndContinues() {
    var process =
        new TransferProcessDefinition(
            "test",
            rawConfig,
            pids -> fromIterable(List.of(PATIENT, DefaultTransferProcessRunnerTest.PATIENT_2)),
            p -> fromIterable(List.of(new ConsentedPatientBundle(new Bundle(), p))),
            b -> just(new TransportBundle(new Bundle(), "transferId")),
            errorOnSecond(new Result()));

    var processId = runner.start(process, List.of());
    waitForCompletion(processId);

    create(runner.status(processId)).assertNext(this::completedWithErrors).verifyComplete();
  }

  private static BundleSender errorOnSecond(Result result) {
    var first = new AtomicBoolean(true);
    return b ->
        first.getAndSet(false)
            ? just(result)
            : Mono.error(new RuntimeException("Cannot send bundle"));
  }

  @Test
  void logErrorIncludesExceptionWhenDebugEnabled() {
    var event = runWithLogLevel(Level.DEBUG);
    assertThat(event.getThrowableProxy()).isNotNull();
    assertThat(event.getThrowableProxy().getMessage()).isEqualTo("Cannot select data");
  }

  @Test
  void logErrorIncludesMessageOnlyWhenDebugDisabled() {
    var event = runWithLogLevel(Level.ERROR);
    assertThat(event.getThrowableProxy()).isNull();
    assertThat(event.getFormattedMessage()).contains("Cannot select data");
  }

  private ILoggingEvent runWithLogLevel(Level level) {
    var logger = (Logger) LoggerFactory.getLogger(DefaultTransferProcessRunner.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    var originalLevel = logger.getLevel();
    logger.setLevel(level);

    try {
      var process = failingDataSelectorProcess();
      var processId = runner.start(process, List.of());
      waitForCompletion(processId);

      var errorEvents =
          appender.list.stream()
              .filter(e -> e.getLevel() == Level.ERROR)
              .filter(e -> e.getFormattedMessage().contains("Failed to"))
              .toList();
      assertThat(errorEvents).isNotEmpty();
      return errorEvents.getFirst();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
    }
  }

  private TransferProcessDefinition failingDataSelectorProcess() {
    return new TransferProcessDefinition(
        "test",
        rawConfig,
        pids -> fromIterable(List.of(PATIENT)),
        p -> Flux.error(new RuntimeException("Cannot select data")),
        b -> just(new TransportBundle(new Bundle(), "transferId")),
        b -> just(new Result()));
  }

  private void waitForCompletion(String processId) {
    Flux.interval(Duration.ofMillis(10))
        .flatMap(i -> runner.status(processId))
        .takeUntil(s -> TransferProcessStatus.isCompleted(s.phase()))
        .take(Duration.ofSeconds(5))
        .last()
        .as(StepVerifier::create)
        .expectNextCount(1)
        .verifyComplete();
  }
}
