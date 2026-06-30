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
   * Deidentifies a batch of patient bundles. The default implementation processes each bundle
   * independently and isolates per-patient failures. Implementations that talk to the TCA should
   * override this to collapse the per-patient pseudonym lookups into a single batched request per
   * domain.
   *
   * @param bundles the patient bundles to deidentify
   * @return one {@link DeidentificationResult} per bundle that produced transport mappings; bundles
   *     without any mappings are dropped
   */
  default Flux<DeidentificationResult> deidentify(List<ConsentedPatientBundle> bundles) {
    return Flux.fromIterable(bundles)
        .flatMap(
            b ->
                deidentify(b)
                    .<DeidentificationResult>map(
                        tb -> new DeidentificationResult.Success(b.consentedPatient(), tb))
                    .onErrorResume(
                        e ->
                            Mono.just(
                                new DeidentificationResult.Failure(b.consentedPatient(), e))));
  }

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
