package care.smith.fts.cda.test;

import care.smith.fts.api.DeidentificationProvider;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;

@Component("mockDeidentificationProvider")
public class MockDeidentificationProvider
    implements DeidentificationProvider.Factory<Bundle, MockDeidentificationProvider.Config> {
  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public DeidentificationProvider<Bundle> create(
      DeidentificationProvider.Config commonConfig, Config implConfig) {
    return (b, p) -> {
      throw new UnsupportedOperationException();
    };
  }

  public record Config(boolean deidentify) {}
}
