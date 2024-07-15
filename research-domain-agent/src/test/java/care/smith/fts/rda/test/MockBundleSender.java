package care.smith.fts.rda.test;

import care.smith.fts.api.rda.BundleSender;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component("mockBundleSender")
public class MockBundleSender implements BundleSender.Factory<MockBundleSender.Config> {
  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public BundleSender create(BundleSender.Config commonConfig, Config implConfig) {
    return (b) -> {
      throw new UnsupportedOperationException();
    };
  }

  public record Config(Set<String> expect) {}
}
