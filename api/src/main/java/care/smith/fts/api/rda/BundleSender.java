package care.smith.fts.api.rda;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;

import care.smith.fts.api.TransferProcessStep;
import care.smith.fts.api.TransferProcessStepFactory;
import org.hl7.fhir.r4.model.Bundle;
import reactor.core.publisher.Mono;

public interface BundleSender extends TransferProcessStep {

  Mono<Result> send(Bundle bundles);

  /**
   * Stable grouping key for the downstream store this sender targets. Senders that hit a concrete
   * store override this; the default falls back to a per-instance key, so unidentified senders are
   * very unlikely to share each other's concurrency budget.
   */
  default String destinationId() {
    return "ungrouped-%s@%s".formatted(getClass().getName(), toHexString(identityHashCode(this)));
  }

  interface Factory<C> extends TransferProcessStepFactory<BundleSender, Config, C> {}

  record Config() {}

  record Result() {}
}
