package care.smith.fts.api;

import org.hl7.fhir.instance.model.api.IBaseBundle;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BundleSender<B extends IBaseBundle> {

  Mono<Result> send(Flux<B> bundle, ConsentedPatient patient);

  interface Factory<B extends IBaseBundle, C> extends StepFactory<BundleSender<B>, Config, C> {}

  record Config(String project) {}

  record Result() {}
}
