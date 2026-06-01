package care.smith.fts.rda;

import static care.smith.fts.util.JsonLogFormatter.asJson;
import static java.util.stream.Collectors.groupingBy;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.rda.AdmissionController.AdmitResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Backpressure-aware runner.
 *
 * <p>Three tiers protect the system:
 *
 * <ol>
 *   <li>a global hard cap and an elastic per-project fair share (see {@link AdmissionController});
 *   <li>a per-HDS drain concurrency: bundles for a given FHIR store are funnelled through one
 *       {@link reactor.core.publisher.Sinks.Many sink} drained by a single {@code flatMap(N_H)}
 *       subscribed once at construction. {@code N_H} is the conservative minimum of {@code
 *       sendConcurrency()} across the projects sharing that destination.
 * </ol>
 *
 * The drainer is subscribed exactly once and must never terminate: each item's pipeline isolates
 * its own error via {@code onErrorResume}, and the sink is never completed.
 */
@Slf4j
@Component
public class DefaultTransferProcessRunner implements TransferProcessRunner {

  private final Map<String, ProcessStatus> statuses = new ConcurrentHashMap<>();
  private final Map<String, HdsDrainer> drainersByProject = new ConcurrentHashMap<>();
  private final ObjectMapper om;
  private final AdmissionController admission;

  public DefaultTransferProcessRunner(
      ObjectMapper om, List<TransferProcessDefinition> processes, RdaRunnerConfig config) {
    this.om = om;
    this.admission = new AdmissionController(config.getGlobalBufferMax());
    buildDrainers(processes);
  }

  /** Group definitions by HDS destination, compute N_H per HDS, and wire one drainer per HDS. */
  private void buildDrainers(List<TransferProcessDefinition> processes) {
    Map<String, List<TransferProcessDefinition>> byDestination =
        processes.stream().collect(groupingBy(p -> p.bundleSender().destinationId()));

    byDestination.forEach(
        (destination, defs) -> {
          int nH = resolveSendConcurrency(destination, defs);
          var drainer = new HdsDrainer(destination, nH, admission);
          defs.forEach(def -> drainersByProject.put(def.project(), drainer));
        });
  }

  /** N_H = min(sendConcurrency) across the destination's projects; WARN on a mismatch. */
  private static int resolveSendConcurrency(
      String destination, List<TransferProcessDefinition> defs) {
    var concurrencies = defs.stream().map(d -> d.bundleSender().sendConcurrency()).toList();
    int min = concurrencies.stream().mapToInt(Integer::intValue).min().orElse(1);
    if (concurrencies.stream().distinct().count() > 1) {
      log.warn(
          "Projects sharing HDS '{}' declare different sendConcurrency values {}; "
              + "using the conservative minimum {}",
          destination,
          concurrencies,
          min);
    }
    return min;
  }

  @Override
  public StartResult start(TransferProcessDefinition process, Mono<TransportBundle> data) {
    var project = process.project();
    if (admission.admit(project) == AdmitResult.REJECT) {
      log.debug("Rejected bundle for project '{}' (backpressure)", project);
      return StartResult.rejected();
    }

    var processId = UUID.randomUUID().toString();
    log.info("Run process with processId: {}", processId);
    log.info("Project configuration: {}", asJson(om, process.rawConfig()));

    var status = new ProcessStatus();
    statuses.put(processId, status);

    var item =
        new DrainItem(project, status, process.deidentificator(), process.bundleSender(), data);

    HdsDrainer drainer = drainersByProject.get(project);
    if (drainer == null) {
      // Should not happen: every configured project is wired at construction. Release the permit so
      // the invariant holds, and report rejection rather than leaking.
      log.error("No drainer for project '{}'; releasing admission permit", project);
      admission.release(project);
      statuses.remove(processId);
      return StartResult.rejected();
    }
    drainer.submit(item);
    return StartResult.accepted(processId);
  }

  /** Test/maintenance hook: number of admission permits currently in use across all projects. */
  int permitsInUse() {
    return admission.permitsInUse();
  }

  /** Test hook: per-HDS drain concurrency resolved for the given project. */
  int drainerConcurrencyFor(String project) {
    return drainersByProject.get(project).sendConcurrency();
  }

  /** Cancels every drainer's subscription, propagating cancellation to in-flight sends. */
  void disposeAllDrainers() {
    drainersByProject.values().stream().distinct().forEach(HdsDrainer::dispose);
  }

  @Override
  public Mono<Status> status(String processId) {
    ProcessStatus status = statuses.get(processId);
    if (status != null) {
      return Mono.just(status.snapshot(processId));
    } else {
      return Mono.error(new IllegalArgumentException());
    }
  }

  /** Mutable per-process status carried by the drained item and read by {@link #status}. */
  static final class ProcessStatus {
    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.RUNNING);
    private final AtomicLong receivedResources = new AtomicLong();
    private final AtomicLong sentResources = new AtomicLong();

    void addReceived(long n) {
      receivedResources.getAndAdd(n);
    }

    void addSent(long n) {
      sentResources.getAndAdd(n);
    }

    void setPhase(Phase p) {
      phase.set(p);
    }

    Status snapshot(String processId) {
      return new Status(processId, phase.get(), receivedResources.get(), sentResources.get());
    }
  }

  /** One unit of work funnelled to an HDS drainer. */
  record DrainItem(
      String project,
      ProcessStatus status,
      Deidentificator deidentificator,
      BundleSender bundleSender,
      Mono<TransportBundle> data) {

    Mono<Bundle> deidentify() {
      return data.doOnNext(b -> status.addReceived(b.bundle().getEntry().size()))
          .flatMap(deidentificator::deidentify);
    }
  }
}
