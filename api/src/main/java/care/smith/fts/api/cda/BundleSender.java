package care.smith.fts.api.cda;

import care.smith.fts.api.Step;
import care.smith.fts.api.StepFactory;
import care.smith.fts.api.TransportBundle;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BundleSender extends Step {

  Mono<Result> send(Flux<TransportBundle> bundles);

  interface Factory<C> extends StepFactory<BundleSender, Config, C> {}

  record Config() {}

  record Result(int bundleCount) {}
}
