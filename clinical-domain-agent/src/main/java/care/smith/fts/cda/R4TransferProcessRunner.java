package care.smith.fts.cda;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class R4TransferProcessRunner {

  // TODO use pool
  private final ForkJoinPool pool;

    public R4TransferProcessRunner(@Qualifier("transferProcessPool") ForkJoinPool pool) {
        this.pool = pool;
    }

    public List<Boolean> run(TransferProcess process) {
    return process.getCohortSelector().selectCohort().parallelStream()
        .map(process.getDataSelector()::select)
        .map(process.getDeidentificationProvider()::deidentify)
        .map(process.getBundleSender()::send)
        .toList();
  }
}
