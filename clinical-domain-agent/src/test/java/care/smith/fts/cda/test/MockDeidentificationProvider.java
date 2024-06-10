package care.smith.fts.cda.test;

import care.smith.fts.api.ConsentedPatient;
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
    return new Impl(implConfig);
  }

  public record Config(boolean deidentify) {}

  public static class Impl implements DeidentificationProvider<Bundle> {
    private final MockDeidentificationProvider.Config implConfig;

    public Impl(MockDeidentificationProvider.Config implConfig) {
      this.implConfig = implConfig;
    }

    @Override
    public Bundle deidentify(Bundle b, ConsentedPatient ignored) {
      if (implConfig.deidentify()) {
        return scramble(b);
      } else {
        return b;
      }
    }

    private Bundle scramble(Bundle ignored) {
      return null;
    }
  }
}
