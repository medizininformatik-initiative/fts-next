package care.smith.fts.rda;

import static org.assertj.core.api.Assertions.assertThat;
import static reactor.core.publisher.Mono.just;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.rda.DefaultTransferProcessRunner.DrainItem;
import care.smith.fts.rda.DefaultTransferProcessRunner.ProcessStatus;
import care.smith.fts.rda.TransferProcessRunner.Phase;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.publisher.Sinks;

class HdsDrainerTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(3);

  private static void await(BooleanSupplier condition) {
    var deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("condition not met within " + TIMEOUT);
      }
      Thread.onSpinWait();
    }
  }

  private static Bundle oneEntryBundle() {
    return new Bundle().addEntry(new Bundle().getEntryFirstRep());
  }

  private static final Deidentificator IDENTITY = (b) -> just(oneEntryBundle());

  private static DrainItem item(String project, ProcessStatus status, BundleSender sender) {
    return new DrainItem(
        project, status, IDENTITY, sender, Mono.just(new TransportBundle(oneEntryBundle(), "tid")));
  }

  @Test
  void concurrentInFlightNeverExceedsNh() {
    int nH = 2;
    var admission = new AdmissionController(100);
    var sender = new GatedSender();
    var drainer = new HdsDrainer("http://blaze/fhir", nH, admission);

    // submit 5 items; only N_H may be in-flight at once
    for (int i = 0; i < 5; i++) {
      admission.admit("p");
      drainer.submit(item("p", new ProcessStatus(), sender));
    }

    await(() -> sender.inFlight.get() == nH);
    // give the drainer a chance to (wrongly) over-admit; it must stay at N_H
    assertThat(sender.peakInFlight.get()).isEqualTo(nH);
    assertThat(sender.inFlight.get()).isEqualTo(nH);

    // drain everything; peak must never have exceeded N_H
    sender.releaseAll(5);
    await(() -> admission.permitsInUse() == 0);
    assertThat(sender.peakInFlight.get()).isLessThanOrEqualTo(nH);

    drainer.dispose();
  }

  @Test
  void overflowBuffersWithoutBlockingSubmit() {
    var admission = new AdmissionController(100);
    var sender = new GatedSender();
    var drainer = new HdsDrainer("http://blaze/fhir", 1, admission);

    // Submitting many more than N_H must return immediately (non-blocking); extras buffer.
    long start = System.nanoTime();
    for (int i = 0; i < 20; i++) {
      admission.admit("p");
      drainer.submit(item("p", new ProcessStatus(), sender));
    }
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
    assertThat(elapsedMs).isLessThan(500);

    await(() -> sender.inFlight.get() == 1);
    assertThat(sender.peakInFlight.get()).isEqualTo(1);

    sender.releaseAll(20);
    await(() -> admission.permitsInUse() == 0);

    drainer.dispose();
  }

  @Test
  void errorOnOneItemDoesNotTerminateDrainer() {
    var admission = new AdmissionController(100);
    BundleSender failing = (b) -> Mono.error(new RuntimeException("boom"));
    var drainer = new HdsDrainer("http://blaze/fhir", 1, admission);

    var s1 = new ProcessStatus();
    admission.admit("p");
    drainer.submit(item("p", s1, failing));
    await(() -> s1.snapshot("1").phase() == Phase.ERROR);

    // drainer must still process subsequent items
    var ok = new GatedSender();
    var s2 = new ProcessStatus();
    admission.admit("p");
    drainer.submit(item("p", s2, ok));
    await(() -> ok.inFlight.get() == 1);
    ok.releaseAll(1);
    await(() -> s2.snapshot("2").phase() == Phase.COMPLETED);
    await(() -> admission.permitsInUse() == 0);

    drainer.dispose();
  }

  @Test
  void exposesDestinationId() {
    var drainer = new HdsDrainer("http://blaze:8080/fhir", 1, new AdmissionController(1));
    assertThat(drainer.destinationId()).isEqualTo("http://blaze:8080/fhir");
    drainer.dispose();
  }

  /**
   * The drain is designed never to terminate, so an upstream error is "should not happen". We force
   * one through the sink (via reflection) to exercise the defensive error callback that logs it.
   */
  @Test
  void unexpectedUpstreamErrorIsLogged() throws Exception {
    var logger = (Logger) LoggerFactory.getLogger(HdsDrainer.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var drainer = new HdsDrainer("http://blaze/fhir", 1, new AdmissionController(1));

      Field sinkField = HdsDrainer.class.getDeclaredField("sink");
      sinkField.setAccessible(true);
      @SuppressWarnings("unchecked")
      Sinks.Many<DrainItem> sink = (Sinks.Many<DrainItem>) sinkField.get(drainer);
      sink.tryEmitError(new RuntimeException("forced"));

      await(
          () ->
              appender.list.stream()
                  .anyMatch(e -> e.getFormattedMessage().contains("terminated unexpectedly")));
    } finally {
      logger.detachAppender(appender);
    }
  }

  /** Completion is equally "should not happen"; forcing it exercises the complete callback. */
  @Test
  void unexpectedCompletionIsLogged() throws Exception {
    var logger = (Logger) LoggerFactory.getLogger(HdsDrainer.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var drainer = new HdsDrainer("http://blaze/fhir", 1, new AdmissionController(1));

      Field sinkField = HdsDrainer.class.getDeclaredField("sink");
      sinkField.setAccessible(true);
      @SuppressWarnings("unchecked")
      Sinks.Many<DrainItem> sink = (Sinks.Many<DrainItem>) sinkField.get(drainer);
      sink.tryEmitComplete();

      await(
          () ->
              appender.list.stream()
                  .anyMatch(e -> e.getFormattedMessage().contains("completed unexpectedly")));
    } finally {
      logger.detachAppender(appender);
    }
  }

  /**
   * Sender whose sends stay in-flight until explicitly released. In-flight is measured as the
   * window from subscription (request issued) until just before the terminal signal is emitted,
   * which is the semantically meaningful "concurrent requests against the HDS" window.
   * (Decrementing in doFinally would race with flatMap subscribing the replacement inner,
   * transiently reading N_H+1 even though only N_H requests are actually outstanding.)
   */
  private static final class GatedSender implements BundleSender {
    final AtomicInteger inFlight = new AtomicInteger();
    final AtomicInteger peakInFlight = new AtomicInteger();
    private final java.util.concurrent.ConcurrentLinkedQueue<MonoSink<Result>> pending =
        new java.util.concurrent.ConcurrentLinkedQueue<>();

    @Override
    public Mono<Result> send(Bundle bundles) {
      return Mono.create(
          sink -> {
            pending.add(sink);
            int now = inFlight.incrementAndGet();
            peakInFlight.accumulateAndGet(now, Math::max);
          });
    }

    void releaseAll(int expected) {
      for (int released = 0; released < expected; ) {
        var sink = pending.poll();
        if (sink != null) {
          inFlight.decrementAndGet();
          sink.success(new Result());
          released++;
        } else {
          Thread.onSpinWait();
        }
      }
    }
  }
}
