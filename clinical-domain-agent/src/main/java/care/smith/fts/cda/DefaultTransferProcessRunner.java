package care.smith.fts.cda;

import care.smith.fts.api.*;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.api.cda.DeidentificationProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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
    Flux<ConsentedPatient> patientFlux = cohortSelector.selectCohort();
    return patientFlux.flatMap(
        patient -> {
          Flux<ConsentedPatientBundle> data =
              bundleDataSelector.select(patient).map(b -> new ConsentedPatientBundle(b, patient));
          Flux<TransportBundle> transportBundleFlux =
              bundleDeidentificationProvider.deidentify(data);
          return bundleBundleSender.send(transportBundleFlux).map(i -> new Result(patient));
        });
  }

  public record Result(ConsentedPatient patient) {}
}
