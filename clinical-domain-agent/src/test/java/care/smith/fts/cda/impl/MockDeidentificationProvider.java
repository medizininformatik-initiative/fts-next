package care.smith.fts.cda.impl;

import care.smith.fts.api.DeidentificationProvider;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.springframework.stereotype.Component;

@Component("mockDeidentificationProvider")
public class MockDeidentificationProvider
    implements DeidentificationProvider.Factory<MockDeidentificationProvider.Config> {
  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public DeidentificationProvider create(
      DeidentificationProvider.Config commonConfig, Config implConfig) {
    return new Impl(implConfig);
  }

  public record Config(boolean deidentify) {}

  public static class Impl implements DeidentificationProvider {
    private final MockDeidentificationProvider.Config implConfig;

    public Impl(MockDeidentificationProvider.Config implConfig) {
      this.implConfig = implConfig;
    }

    @Override
    public IBaseBundle deidentify(IBaseBundle b) {
      if (implConfig.deidentify()) {
        return scramble(b);
      } else {
        return b;
      }
    }

    private IBaseBundle scramble(IBaseBundle b) {
      return null;
    }
  }
}
