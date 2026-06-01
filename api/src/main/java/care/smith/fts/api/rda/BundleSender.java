package care.smith.fts.api.rda;

import care.smith.fts.api.TransferProcessStep;
import care.smith.fts.api.TransferProcessStepFactory;
import org.hl7.fhir.r4.model.Bundle;
import reactor.core.publisher.Mono;

public interface BundleSender extends TransferProcessStep {

  Mono<Result> send(Bundle bundles);

  /**
   * Identifies the downstream HDS (FHIR store) this sender targets. Senders sharing the same
   * destination must report an identical id so the runner can group them behind a single per-HDS
   * drainer. The default returns a per-instance-unique value so senders without a real destination
   * (e.g. test stubs) stay ungrouped.
   */
  default String destinationId() {
    return "ungrouped-" + System.identityHashCode(this);
  }

  /**
   * The maximum number of concurrent sends this sender's HDS can tolerate. Used by the runner to
   * size the per-HDS drain concurrency. Matches {@code FhirStoreBundleSenderConfig}'s default.
   */
  default int sendConcurrency() {
    return 2;
  }

  interface Factory<C> extends TransferProcessStepFactory<BundleSender, Config, C> {}

  record Config() {}

  record Result() {}
}
