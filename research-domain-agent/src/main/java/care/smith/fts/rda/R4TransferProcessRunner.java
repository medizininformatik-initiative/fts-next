package care.smith.fts.rda;

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

  public Boolean run(TransferProcess<Bundle> process, Bundle data) {
    Bundle deidentified = process.deidentificationProvider().deidentify(data, null);
    return process.bundleSender().send(deidentified, process.project());
  }
}
