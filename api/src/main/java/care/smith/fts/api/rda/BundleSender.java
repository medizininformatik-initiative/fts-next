package care.smith.fts.api.rda;

import care.smith.fts.api.TransferProcessStep;
import care.smith.fts.api.TransferProcessStepFactory;
import org.hl7.fhir.r4.model.Bundle;
import reactor.core.publisher.Mono;

public interface BundleSender extends TransferProcessStep {

  Mono<Result> send(Bundle bundles);

  interface Factory<C> extends TransferProcessStepFactory<BundleSender, Config, C> {}

  record Config() {}

  record Result() {}
}
