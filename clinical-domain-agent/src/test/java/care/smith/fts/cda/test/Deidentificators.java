package care.smith.fts.cda.test;

import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.api.cda.Deidentificator.DeidentificationResult;
import java.util.List;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Test factory for {@link Deidentificator}s that only care about single-bundle behavior. Wraps a
 * per-bundle function into a full {@link Deidentificator}, deriving the batch method from it with
 * the same per-patient failure isolation the production interface used to provide as a default.
 */
public final class Deidentificators {

  private Deidentificators() {}

  public static Deidentificator perBundle(
      Function<ConsentedPatientBundle, Mono<TransportBundle>> fn) {
    return new Deidentificator() {
      @Override
      public Mono<TransportBundle> deidentify(ConsentedPatientBundle bundle) {
        return fn.apply(bundle);
      }

      @Override
      public Flux<DeidentificationResult> deidentify(List<ConsentedPatientBundle> bundles) {
        return Flux.fromIterable(bundles)
            .flatMap(
                b ->
                    fn.apply(b)
                        .<DeidentificationResult>map(
                            tb -> new DeidentificationResult.Success(b.consentedPatient(), tb))
                        .onErrorResume(
                            e ->
                                Mono.just(
                                    new DeidentificationResult.Failure(b.consentedPatient(), e))));
      }
    };
  }
}
