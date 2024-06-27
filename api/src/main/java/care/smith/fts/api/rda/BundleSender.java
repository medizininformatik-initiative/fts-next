package care.smith.fts.api.rda;

import care.smith.fts.api.Step;
import care.smith.fts.api.StepFactory;
import org.hl7.fhir.r4.model.Bundle;
import reactor.core.publisher.Mono;

public interface BundleSender extends Step {

  Mono<Result> send(Bundle bundles);

  interface Factory<C> extends StepFactory<BundleSender, Config, C> {}

  record Config() {}

  record Result() {}
}
