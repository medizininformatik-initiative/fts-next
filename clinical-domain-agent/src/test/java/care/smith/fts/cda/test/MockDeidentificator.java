package care.smith.fts.cda.test;

import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.Deidentificator;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component("mockDeidentificator")
public class MockDeidentificator implements Deidentificator.Factory<MockDeidentificator.Config> {
  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public Deidentificator create(Deidentificator.Config commonConfig, Config implConfig) {
    return new Deidentificator() {
      @Override
      public Mono<TransportBundle> deidentify(ConsentedPatientBundle bundle) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Flux<DeidentificationResult> deidentify(List<ConsentedPatientBundle> bundles) {
        throw new UnsupportedOperationException();
      }
    };
  }

  public record Config(boolean deidentify) {}
}
