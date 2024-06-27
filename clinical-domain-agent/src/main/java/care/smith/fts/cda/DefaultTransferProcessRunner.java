package care.smith.fts.cda;

import care.smith.fts.api.*;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.api.cda.DeidentificationProvider;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
public class DefaultTransferProcessRunner implements TransferProcessRunner {

  @Override
  public Flux<Result> run(TransferProcess process) {
    return runProcess(
        process.cohortSelector(),
        process.dataSelector(),
        process.deidentificationProvider(),
        process.bundleSender());
  }

  private static Flux<Result> runProcess(
      CohortSelector cohortSelector,
      DataSelector bundleDataSelector,
      DeidentificationProvider bundleDeidentificationProvider,
      BundleSender bundleBundleSender) {
    Flux<ConsentedPatient> patients = cohortSelector.selectCohort();
    return patients.flatMap(
        patient -> {
          var selectedResources = new AtomicLong();
          Flux<ConsentedPatientBundle> data =
              bundleDataSelector
                  .select(patient)
                  .doOnNext(b -> selectedResources.getAndAdd(b.getTotal()))
                  .map(b -> new ConsentedPatientBundle(b, patient));

          var deidentifiedResources = new AtomicLong();
          var transportIds = new AtomicLong();
          Flux<TransportBundle> transportBundleFlux =
              bundleDeidentificationProvider
                  .deidentify(data)
                  .doOnNext(b -> deidentifiedResources.getAndAdd(b.bundle().getTotal()))
                  .doOnNext(b -> transportIds.getAndAdd(b.transportIds().size()));
          return bundleBundleSender
              .send(transportBundleFlux)
              .map(
                  sendResult ->
                      Result.builder()
                          .patient(patient)
                          .bundlesSent(sendResult.bundleCount())
                          .selectedResources(selectedResources.get())
                          .deidentifedResource(deidentifiedResources.get())
                          .transportIds(transportIds.get())
                          .build());
        });
  }
}
