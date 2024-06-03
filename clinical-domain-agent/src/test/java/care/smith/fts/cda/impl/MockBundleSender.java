package care.smith.fts.cda.impl;

import care.smith.fts.api.BundleSender;
import java.util.Set;
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
    return bundle -> {
      return ((Bundle) bundle)
          .getEntry().stream()
              .allMatch(entry -> implConfig.expect().contains(entry.getResource().getId()));
      // TODO What if not R4?
    };
  }

  public record Config(Set<String> expect) {}
}
