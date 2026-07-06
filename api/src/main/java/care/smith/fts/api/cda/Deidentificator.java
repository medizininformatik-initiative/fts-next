package care.smith.fts.api.cda;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.TransferProcessStep;
import care.smith.fts.api.TransferProcessStepFactory;
import care.smith.fts.api.TransportBundle;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Deidentificator extends TransferProcessStep {

  Mono<TransportBundle> deidentify(ConsentedPatientBundle bundle);

  /**
   * Deidentifies a batch of patient bundles, collapsing the per-patient pseudonym lookups into a
   * single batched request per domain. Implementations isolate per-patient failures so that a single
   * bad bundle does not fail the whole batch.
   *
   * @param bundles the patient bundles to deidentify
   * @return one {@link DeidentificationResult} per bundle that produced transport mappings; bundles
   *     without any mappings are dropped
   */
  Flux<DeidentificationResult> deidentify(List<ConsentedPatientBundle> bundles);

  /** Outcome of deidentifying a single patient bundle within a batch. */
  sealed interface DeidentificationResult {
    ConsentedPatient patient();

    record Success(ConsentedPatient patient, TransportBundle bundle)
        implements DeidentificationResult {}

    record Failure(ConsentedPatient patient, Throwable error) implements DeidentificationResult {}
  }

  interface Factory<C> extends TransferProcessStepFactory<Deidentificator, Config, C> {}

  record Config() {}
}
