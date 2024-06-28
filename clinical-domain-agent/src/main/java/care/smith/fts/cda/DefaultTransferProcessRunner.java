package care.smith.fts.cda;

import care.smith.fts.api.*;
import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.api.cda.DeidentificationProvider;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class DefaultTransferProcessRunner implements TransferProcessRunner {

  @Override
  public Mono<Result> run(TransferProcess process) {
    return runProcess(
        process.cohortSelector(),
        process.dataSelector(),
        process.deidentificationProvider(),
        process.bundleSender());
  }

  private static Mono<Result> runProcess(
      CohortSelector cohortSelector,
      DataSelector bundleDataSelector,
      DeidentificationProvider bundleDeidentificationProvider,
      BundleSender bundleBundleSender) {
    var errors = new AtomicLong();
    return cohortSelector
        .selectCohort()
        .flatMap(
            patient -> {
              var selectedResources = new AtomicLong();
              Flux<ConsentedPatientBundle> data =
                  bundleDataSelector
                      .select(patient)
                      .doOnNext(b -> selectedResources.getAndAdd(b.getEntry().size()))
                      .map(b -> new ConsentedPatientBundle(b, patient));

              var deidentifiedResources = new AtomicLong();
              var transportIds = new AtomicLong();
              Flux<TransportBundle> transportBundleFlux =
                  bundleDeidentificationProvider
                      .deidentify(data)
                      .doOnNext(b -> deidentifiedResources.getAndAdd(b.bundle().getEntry().size()))
                      .doOnNext(b -> transportIds.getAndAdd(b.transportIds().size()));
              return bundleBundleSender
                  .send(transportBundleFlux)
                  .map(
                      sendResult ->
                          new PatientResult(
                              sendResult.bundleCount(),
                              selectedResources.get(),
                              deidentifiedResources.get(),
                              transportIds.get()));
            })
        .doOnError(e -> errors.incrementAndGet())
        .onErrorContinue((err, o) -> log.info("Could not process patient: {}", err.getMessage()))
        .collectList()
        .map(ps -> createResult(ps, errors));
  }

  private static Result createResult(List<PatientResult> ps, AtomicLong errors) {
    long sumBundlesSent = ps.stream().mapToLong(PatientResult::bundlesSentCount).sum();
    return new Result(sumBundlesSent, errors.get(), ps);
  }
}
