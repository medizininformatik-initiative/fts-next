package care.smith.fts.cda;

import care.smith.fts.api.*;
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
      DeidentificationProvider<Bundle> bundleDeidentificationProvider,
      BundleSender<Bundle> bundleBundleSender) {
    Flux<ConsentedPatient> patientFlux = cohortSelector.selectCohort();
    return patientFlux.flatMap(
        patient -> {
          Flux<Bundle> data = bundleDataSelector.select(patient);
          Flux<Bundle> deidentified = bundleDeidentificationProvider.deidentify(data, patient);
          return bundleBundleSender.send(deidentified, patient).map(i -> new Result(patient));
        });
  }

  public record Result(ConsentedPatient patient) {}
}
