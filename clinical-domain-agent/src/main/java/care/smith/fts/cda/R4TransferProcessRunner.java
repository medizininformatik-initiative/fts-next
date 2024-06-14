package care.smith.fts.cda;

import care.smith.fts.api.*;
import care.smith.fts.cda.services.deidentifhir.ConsentedPatientBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class R4TransferProcessRunner implements TransferProcessRunner {

  @Override
  public Flux<Result> run(TransferProcess<Bundle> process) {
    return runProcess(
        process.cohortSelector(),
        process.dataSelector(),
        process.deidentificationProvider(),
        process.bundleSender());
  }

  private static Flux<Result> runProcess(
      CohortSelector cohortSelector,
      DataSelector<Bundle> bundleDataSelector,
      DeidentificationProvider<ConsentedPatientBundle<Bundle>, TransportBundle<Bundle>>
          bundleDeidentificationProvider,
      BundleSender<Bundle> bundleBundleSender) {
    Flux<ConsentedPatient> patientFlux = cohortSelector.selectCohort();
    return patientFlux.flatMap(
        patient -> {
          Flux<ConsentedPatientBundle<Bundle>> data =
              bundleDataSelector.select(patient).map(b -> new ConsentedPatientBundle<>(b, patient));
          Flux<TransportBundle<Bundle>> transportBundleFlux =
              bundleDeidentificationProvider.deidentify(data);
          return bundleBundleSender.send(transportBundleFlux).map(i -> new Result(patient));
        });
  }

  public record Result(ConsentedPatient patient) {}
}
