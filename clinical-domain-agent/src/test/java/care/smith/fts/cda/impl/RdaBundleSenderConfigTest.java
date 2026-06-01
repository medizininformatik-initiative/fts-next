package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.HttpClientConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RdaBundleSenderConfigTest {

  private static final HttpClientConfig SERVER = new HttpClientConfig("http://localhost");

  @Test
  void nullMaxBackpressureWaitUsesDefault() {
    assertThat(new RdaBundleSenderConfig(SERVER, "project", null))
        .extracting(RdaBundleSenderConfig::maxBackpressureWait)
        .isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  void explicitMaxBackpressureWaitIsKept() {
    var wait = Duration.ofSeconds(30);
    assertThat(new RdaBundleSenderConfig(SERVER, "project", wait))
        .extracting(RdaBundleSenderConfig::maxBackpressureWait)
        .isEqualTo(wait);
  }

  @Test
  void twoArgConstructorUsesDefaultMaxBackpressureWait() {
    var config = new RdaBundleSenderConfig(SERVER, "project");
    assertThat(config.server()).isEqualTo(SERVER);
    assertThat(config.project()).isEqualTo("project");
    assertThat(config.maxBackpressureWait()).isEqualTo(Duration.ofMinutes(10));
  }
}
