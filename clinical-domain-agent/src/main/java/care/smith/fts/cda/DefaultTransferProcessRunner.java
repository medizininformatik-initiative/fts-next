package care.smith.fts.cda;

import care.smith.fts.api.*;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.api.cda.Deidentificator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

@Slf4j
@Component
public class DefaultTransferProcessRunner implements TransferProcessRunner {

  private final Map<String, Many<Process>> sinks = new ConcurrentHashMap<>();
  private final Map<String, TransferProcessInstance> instances = new ConcurrentHashMap<>();

  record ProcessFinished(String processId) implements Process {}

  record ProcessItem(String processId, TransportBundle bundle) implements Process {}

  record ProcessError(String processId, Throwable e) implements Process {}

  @Override
  public String start(TransferProcessDefinition process, List<String> pids) {
    var processId = UUID.randomUUID().toString();
    log.info("Run process with processId: {}", processId);

    if (!sinks.containsKey(process.project())) {
      Many<Process> sink = Sinks.many().unicast().onBackpressureBuffer();
      sinks.put(process.project(), sink);
      sink.asFlux()
          .concatMap(
              p -> {
                if (p instanceof ProcessItem) {
                  log.info("Process item: {}", p);
                  TransferProcessInstance instance = instances.get(processId);
                  return process
                      .bundleSender()
                      .send(((ProcessItem) p).bundle)
                      .doOnNext(
                          r -> {
                            var sentBundles = instance.sentBundles.incrementAndGet();
                            log.info("Success case");
                            if (instance.inputPhaseFinished.get()) {
                              log.info("input phase finished");
                              var skippedBundles = instance.skippedBundles.get();
                              var inputBundles = instance.inputBundles.get();
                              log.info(
                                  "input bundles: {}, skipped bundles: {}",
                                  inputBundles,
                                  skippedBundles);
                              if (sentBundles + skippedBundles == inputBundles) {
                                log.info("Process {} finished", processId);
                                instance.phase.set(Phase.COMPLETED);
                              }
                            }
                          })
                      .doOnError(
                          e -> {
                            log.error("Error case: {}", e.getMessage());
                            var skippedBundles = instance.skippedBundles.incrementAndGet();

                            if (instance.inputPhaseFinished.get()) {
                              log.info("input phase finished");
                              var sentBundles = instance.sentBundles.get();
                              var inputBundles = instance.inputBundles.get();
                              log.info(
                                  "input bundles: {}, skipped bundles: {}",
                                  inputBundles,
                                  skippedBundles);
                              if (sentBundles + skippedBundles == inputBundles) {
                                log.info("Process {} finished", processId);
                                instance.phase.set(Phase.COMPLETED);
                              }
                            }
                          })
                      .onErrorResume(e -> Mono.just(ResponseEntity.ok().build()));

                  //                } else if (p instanceof ProcessFinished) {
                  //                  log.info("Process finished");
                  //                  instances.get(processId).phase.set(Phase.COMPLETED);
                  //                  return Mono.just(ResponseEntity.ok())
                  //                      .doOnNext(r -> log.info("Process finished: {}", r));
                } else {
                  log.info("Process Error");
                  return Mono.just(ResponseEntity.ok());
                }
              },
              32)
          .doOnNext(b -> log.info("Next {}", b))
          .doOnError(e -> log.error("Skipping patient: {}", e.getMessage()))
          .onErrorContinue((e, i) -> log.error(e.getMessage()))
          .subscribe();
    }

    var sink = sinks.get(process.project());
    var transferProcessInstance = new TransferProcessInstance(process, processId, pids, sink);
    transferProcessInstance.execute();
    instances.put(processId, transferProcessInstance);
    return processId;
  }

  @Override
  public Mono<Status> status(String processId) {
    TransferProcessInstance transferProcessInstance = instances.get(processId);
    if (transferProcessInstance != null) {
      return Mono.just(transferProcessInstance.status(processId));
    } else {
      return Mono.error(new IllegalArgumentException());
    }
  }

  public static class TransferProcessInstance {

    private final CohortSelector cohortSelector;
    private final DataSelector dataSelector;
    private final Deidentificator deidentificator;
    private final String processId;
    private final List<String> pids;
    private final AtomicLong inputBundles;
    private final AtomicBoolean inputPhaseFinished;
    private final AtomicLong skippedBundles;
    private final AtomicLong sentBundles;
    private final AtomicReference<Phase> phase;
    private final Many<Process> sink;

    public TransferProcessInstance(
        TransferProcessDefinition process,
        String processId,
        List<String> pids,
        Many<Process> sink) {
      this.processId = processId;
      cohortSelector = process.cohortSelector();
      dataSelector = process.dataSelector();
      deidentificator = process.deidentificator();
      this.pids = pids;
      this.inputBundles = new AtomicLong(0);
      this.inputPhaseFinished = new AtomicBoolean(false);
      this.sink = sink;

      skippedBundles = new AtomicLong();
      sentBundles = new AtomicLong();
      phase = new AtomicReference<>(Phase.QUEUED);
    }

    public void execute() {
      phase.set(Phase.RUNNING);

      cohortSelector
          .selectCohort(pids)
          .doOnError(e -> phase.set(Phase.ERROR))
          .doOnError(e -> sink.tryEmitNext(new ProcessError(processId, e)))
          .flatMap(dataSelector::select)
          .flatMap(deidentificator::deidentify)
          .map(b -> new ProcessItem(processId, b))
          .doOnNext(i -> inputBundles.incrementAndGet())
          .doOnComplete(() -> inputPhaseFinished.set(true))
          .doOnComplete(() -> log.info("Process finished for {}", processId))
          //          .doOnComplete(() -> sink.tryEmitNext(new ProcessFinished(processId)))
          .doOnError(e -> log.error(e.getMessage()))
          .subscribe(sink::tryEmitNext);
    }

    public Status status(String processId) {
      return new Status(processId, phase.get(), sentBundles.get(), skippedBundles.get());
    }
  }
}
