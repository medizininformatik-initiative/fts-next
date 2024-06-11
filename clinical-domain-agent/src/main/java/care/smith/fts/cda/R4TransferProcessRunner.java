package care.smith.fts.cda;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class R4TransferProcessRunner {

  // TODO use pool
  private final ForkJoinPool pool;

  public R4TransferProcessRunner(@Qualifier("transferProcessPool") ForkJoinPool pool) {
    this.pool = pool;
  }

  public List<Boolean> run(TransferProcess<Bundle> process) {
    return process.cohortSelector().selectCohort().parallelStream()
        .map(
            p -> {
              Bundle data = process.dataSelector().select(p);
              Bundle deidentified = process.deidentificationProvider().deidentify(data, p);
              return process.bundleSender().send(deidentified);
            })
        .toList();
  }
}
