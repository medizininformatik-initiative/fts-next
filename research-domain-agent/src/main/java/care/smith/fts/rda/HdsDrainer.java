package care.smith.fts.rda;

import care.smith.fts.rda.DefaultTransferProcessRunner.DrainItem;
import care.smith.fts.rda.TransferProcessRunner.Phase;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Drains the work queued for a single HDS (FHIR store) at a bounded concurrency {@code N_H}.
 *
 * <p>Design constraints:
 *
 * <ul>
 *   <li>The drain is subscribed exactly once (at construction) and must never terminate. Each
 *       item's pipeline isolates its own error with {@code onErrorResume}, and the sink is never
 *       completed, so the outer flux stays alive for the lifetime of the agent.
 *   <li>{@code release} fires on every terminal signal (success, error and cancel) via {@code
 *       doFinally}, so an admitted bundle releases its admission permit exactly once.
 *   <li>Concurrent POSTs from multiple CDAs emit to this sink from different threads. {@code
 *       unicast().onBackpressureBuffer()} is not concurrency-safe for emission, so we serialise
 *       with a busy-looping emit handler that retries on {@code FAIL_NON_SERIALIZED}.
 * </ul>
 */
@Slf4j
final class HdsDrainer {

  /** Retry emit on transient non-serialized contention; never retry on terminated/cancelled. */
  private static final Sinks.EmitFailureHandler EMIT_BUSY_LOOP =
      Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(5));

  private final String destinationId;
  private final int sendConcurrency;
  private final Sinks.Many<DrainItem> sink;
  private final Disposable subscription;

  HdsDrainer(String destinationId, int sendConcurrency, AdmissionController admission) {
    this.destinationId = destinationId;
    this.sendConcurrency = sendConcurrency;
    this.sink = Sinks.many().unicast().onBackpressureBuffer();
    // Each item's pipeline yields Mono<Void>, so the drain never emits a value — only the two
    // "should not happen" terminal signals are observable. They are logged via lifecycle operators
    // (rather than subscribe's value/error consumers) so no unreachable value-consumer remains;
    // onErrorComplete keeps a stray error from reaching the global onErrorDropped hook.
    this.subscription =
        sink.asFlux()
            .flatMap(item -> process(item, admission), sendConcurrency)
            .doOnError(
                error ->
                    log.error("HDS '{}' drainer terminated unexpectedly", destinationId, error))
            .doOnComplete(() -> log.error("HDS '{}' drainer completed unexpectedly", destinationId))
            .onErrorComplete()
            .subscribe();
  }

  /**
   * Per-item pipeline. Errors are confined here so the drainer never terminates. {@code release}
   * runs in {@code doFinally} so it fires on success, error, and cancellation alike.
   */
  private Mono<Void> process(DrainItem item, AdmissionController admission) {
    return item.deidentify()
        .doOnNext(b -> item.status().addSent(b.getEntry().size()))
        .flatMap(item.bundleSender()::send)
        .doOnNext(r -> item.status().setPhase(Phase.COMPLETED))
        .then()
        .onErrorResume(
            e -> {
              log.info("Could not process patient: {}", e.getMessage());
              log.trace("The exception:", e);
              item.status().setPhase(Phase.ERROR);
              return Mono.empty();
            })
        .doFinally(signal -> admission.release(item.project()));
  }

  void submit(DrainItem item) {
    sink.emitNext(item, EMIT_BUSY_LOOP);
  }

  int sendConcurrency() {
    return sendConcurrency;
  }

  String destinationId() {
    return destinationId;
  }

  void dispose() {
    subscription.dispose();
  }
}
