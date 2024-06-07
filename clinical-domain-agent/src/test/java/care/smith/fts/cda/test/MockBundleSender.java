package care.smith.fts.cda.test;

import care.smith.fts.api.BundleSender;
import java.util.Set;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;

@Component("mockBundleSender")
public class MockBundleSender implements BundleSender.Factory<MockBundleSender.Config> {
  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public BundleSender create(BundleSender.Config commonConfig, Config implConfig) {
    return new Impl(implConfig);
  }

  public record Config(Set<String> expect) {}

  public static class Impl implements BundleSender {
    private final MockBundleSender.Config implConfig;

    public Impl(MockBundleSender.Config implConfig) {
      this.implConfig = implConfig;
    }

    @Override
    public boolean send(IBaseBundle bundle) {
      return ((Bundle) bundle)
          .getEntry().stream()
              .allMatch(entry -> implConfig.expect().contains(entry.getResource().getId()));
    }
  }
}
