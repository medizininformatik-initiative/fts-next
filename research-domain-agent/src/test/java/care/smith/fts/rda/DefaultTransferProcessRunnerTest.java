package care.smith.fts.rda;

import static care.smith.fts.api.rda.BundleSender.*;
import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static reactor.core.publisher.Mono.just;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.rda.TransferProcessRunner.Phase;
import care.smith.fts.rda.TransferProcessRunner.StartResult;
import care.smith.fts.rda.TransferProcessRunner.StartResult.Accepted;
import care.smith.fts.rda.TransferProcessRunner.Status;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

class DefaultTransferProcessRunnerTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private static RdaRunnerConfig config(int globalBufferMax) {
    var c = new RdaRunnerConfig();
    c.setGlobalBufferMax(globalBufferMax);
    return c;
  }

  private static Bundle oneEntryBundle() {
    return new Bundle().addEntry(new Bundle().getEntryFirstRep());
  }

  private static TransportBundle transport() {
    return new TransportBundle(oneEntryBundle(), "transferId");
  }

  private static TransferProcessDefinition process(
      String project, Deidentificator deid, BundleSender sender) {
    return new TransferProcessDefinition(
        project, new TransferProcessConfig(null, null), deid, sender);
  }

  private static void await(BooleanSupplier condition) {
    var deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("condition not met within " + TIMEOUT);
      }
      Thread.onSpinWait();
    }
  }

  private static void awaitPhase(
      DefaultTransferProcessRunner runner, String processId, Predicate<Status> p) {
    await(() -> p.test(runner.status(processId).block()));
  }

  @Test
  void runMockTestSuccessfully() {
    BundleSender.Result result = new Result();
    var proc = process("test", (b) -> just(oneEntryBundle()), (b) -> just(result));
    var runner = new DefaultTransferProcessRunner(new ObjectMapper(), of(proc), config(256));

    var start = runner.start(proc, Mono.just(transport()));
    assertThat(start).isInstanceOf(Accepted.class);
    var processId = ((Accepted) start).processId();

    awaitPhase(runner, processId, s -> s.phase() == Phase.COMPLETED);
    var status = runner.status(processId).block();
    assertThat(status.receivedResources()).isEqualTo(1);
    assertThat(status.sentResources()).isEqualTo(1);
  }

  @Test
  void startWithUnconfiguredProjectReleasesPermitAndRejects() {
    // The runner only wires drainers for the projects present at construction. Starting a process
    // whose project was never configured passes admission (global) but finds no drainer; the
    // defensive path must release the permit and reject rather than leak.
    var known = process("known", (b) -> just(oneEntryBundle()), (b) -> just(new Result()));
    var runner = new DefaultTransferProcessRunner(new ObjectMapper(), of(known), config(4));

    var unknown = process("unknown", (b) -> just(oneEntryBundle()), (b) -> just(new Result()));
    var result = runner.start(unknown, Mono.just(transport()));

    assertThat(result).isInstanceOf(StartResult.Rejected.class);
    assertThat(runner.permitsInUse()).isZero();
  }

  @Test
  void statusForUnknownProcessErrors() {
    var runner = new DefaultTransferProcessRunner(new ObjectMapper(), of(), config(4));
    assertThatThrownBy(() -> runner.status("does-not-exist").block())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void admissionRejectsWhenBufferFull() {
    // globalBufferMax = 1: one in-flight bundle that never completes saturates the buffer.
    var blocking = new BlockingSender();
    var proc = process("test", (b) -> just(oneEntryBundle()), blocking);
    var runner = new DefaultTransferProcessRunner(new ObjectMapper(), of(proc), config(1));

    var first = runner.start(proc, Mono.just(transport()));
    assertThat(first).isInstanceOf(Accepted.class);
    await(() -> blocking.inFlight.get() == 1);

    var second = runner.start(proc, Mono.just(transport()));
    assertThat(second).isInstanceOf(StartResult.Rejected.class);

    blocking.complete();
    await(() -> runner.start(proc, Mono.just(transport())) instanceof Accepted);
    blocking.complete();
  }

  @Test
  void permitReleasedOnSendError() {
    // globalBufferMax = 1: if the permit leaked on error, the follow-up admission would be
    // rejected.
    BundleSender failing = (b) -> Mono.error(new RuntimeException("boom"));
    var proc = process("test", (b) -> just(oneEntryBundle()), failing);
    var runner = new DefaultTransferProcessRunner(new ObjectMapper(), of(proc), config(1));

    var processId = ((Accepted) runner.start(proc, Mono.just(transport()))).processId();
    awaitPhase(runner, processId, s -> s.phase() == Phase.ERROR);

    assertThat(runner.start(proc, Mono.just(transport()))).isInstanceOf(Accepted.class);
  }

  @Test
  void permitReleasedOnCancellation() {
    // A send that never terminates; cancelling the drainer subscription must still release.
    var blocking = new BlockingSender();
    var proc = process("test", (b) -> just(oneEntryBundle()), blocking);
    var runner = new DefaultTransferProcessRunner(new ObjectMapper(), of(proc), config(1));

    runner.start(proc, Mono.just(transport()));
    await(() -> blocking.inFlight.get() == 1);

    // Cancel the in-flight send via the drainer's subscription. doFinally(release) must fire.
    runner.disposeAllDrainers();

    await(() -> runner.permitsInUse() == 0);
    assertThat(runner.permitsInUse()).isZero();
  }

  @Test
  void sharedBaseUrlSharesOneDrainerWithMinConcurrency() {
    var senderA = new FixedDestinationSender("http://blaze:8080/fhir", 4);
    var senderB = new FixedDestinationSender("http://blaze:8080/fhir", 2);
    var pA = process("a", (b) -> just(oneEntryBundle()), senderA);
    var pB = process("b", (b) -> just(oneEntryBundle()), senderB);

    var runner = new DefaultTransferProcessRunner(new ObjectMapper(), List.of(pA, pB), config(256));

    assertThat(runner.drainerConcurrencyFor("a")).isEqualTo(2);
    assertThat(runner.drainerConcurrencyFor("b")).isEqualTo(2);
    assertThat(runner.drainerConcurrencyFor("a")).isEqualTo(runner.drainerConcurrencyFor("b"));
    assertThat(runner.start(pA, Mono.just(transport()))).isInstanceOf(Accepted.class);
    assertThat(runner.start(pB, Mono.just(transport()))).isInstanceOf(Accepted.class);
  }

  @Test
  void mismatchedSendConcurrencyOnSharedHdsLogsWarn() {
    var logger = (Logger) LoggerFactory.getLogger(DefaultTransferProcessRunner.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var pA =
          process(
              "a",
              (b) -> just(oneEntryBundle()),
              new FixedDestinationSender("http://blaze:8080/fhir", 4));
      var pB =
          process(
              "b",
              (b) -> just(oneEntryBundle()),
              new FixedDestinationSender("http://blaze:8080/fhir", 2));
      new DefaultTransferProcessRunner(new ObjectMapper(), List.of(pA, pB), config(256));

      assertThat(appender.list)
          .anySatisfy(
              e -> {
                assertThat(e.getLevel()).isEqualTo(Level.WARN);
                assertThat(e.getFormattedMessage()).contains("different sendConcurrency");
              });
    } finally {
      logger.detachAppender(appender);
    }
  }

  @Test
  void matchedSendConcurrencyOnSharedHdsDoesNotWarn() {
    var logger = (Logger) LoggerFactory.getLogger(DefaultTransferProcessRunner.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var pA =
          process(
              "a",
              (b) -> just(oneEntryBundle()),
              new FixedDestinationSender("http://blaze:8080/fhir", 2));
      var pB =
          process(
              "b",
              (b) -> just(oneEntryBundle()),
              new FixedDestinationSender("http://blaze:8080/fhir", 2));
      new DefaultTransferProcessRunner(new ObjectMapper(), List.of(pA, pB), config(256));

      assertThat(appender.list).noneSatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.WARN));
    } finally {
      logger.detachAppender(appender);
    }
  }

  /** Sender that holds each send open until {@link #complete()} releases one. */
  private static final class BlockingSender implements BundleSender {
    final AtomicInteger inFlight = new AtomicInteger();
    private volatile MonoSink<Result> pending;

    @Override
    public Mono<Result> send(Bundle bundles) {
      return Mono.<Result>create(
              sink -> {
                pending = sink;
                inFlight.incrementAndGet();
              })
          .doFinally(s -> inFlight.decrementAndGet());
    }

    void complete() {
      await(() -> pending != null);
      var p = pending;
      pending = null;
      p.success(new Result());
    }
  }

  private static final class FixedDestinationSender implements BundleSender {
    private final String destinationId;
    private final int sendConcurrency;

    FixedDestinationSender(String destinationId, int sendConcurrency) {
      this.destinationId = destinationId;
      this.sendConcurrency = sendConcurrency;
    }

    @Override
    public String destinationId() {
      return destinationId;
    }

    @Override
    public int sendConcurrency() {
      return sendConcurrency;
    }

    @Override
    public Mono<Result> send(Bundle bundles) {
      return just(new Result());
    }
  }
}
