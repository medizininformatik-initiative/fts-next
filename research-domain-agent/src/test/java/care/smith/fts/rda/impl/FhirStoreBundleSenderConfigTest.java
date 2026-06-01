package care.smith.fts.rda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.HttpClientConfig;
import org.junit.jupiter.api.Test;

class FhirStoreBundleSenderConfigTest {

  private static final HttpClientConfig SERVER = new HttpClientConfig("http://localhost");

  @Test
  void nullMaxConcurrencyUsesDefault() {
    assertThat(new FhirStoreBundleSenderConfig(SERVER, "project", null))
        .extracting(FhirStoreBundleSenderConfig::maxConcurrency)
        .isEqualTo(2);
  }

  @Test
  void explicitMaxConcurrencyIsKept() {
    assertThat(new FhirStoreBundleSenderConfig(SERVER, "project", 5))
        .extracting(FhirStoreBundleSenderConfig::maxConcurrency)
        .isEqualTo(5);
  }

  @Test
  void twoArgConstructorUsesDefaultMaxConcurrency() {
    var config = new FhirStoreBundleSenderConfig(SERVER, "project");
    assertThat(config.server()).isEqualTo(SERVER);
    assertThat(config.project()).isEqualTo("project");
    assertThat(config.maxConcurrency()).isEqualTo(2);
  }
}
