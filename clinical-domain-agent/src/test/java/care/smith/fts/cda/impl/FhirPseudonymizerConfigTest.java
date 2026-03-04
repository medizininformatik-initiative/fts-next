package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.tca.TcaDomains;
import java.io.File;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FhirPseudonymizerConfigTest {

  @Test
  void dateShiftPreserveDefaultsToNoneWhenNull() {
    var serviceUrl = new HttpClientConfig("http://localhost:1234");
    var tcaServer = new HttpClientConfig("http://localhost:5678");
    var domains = new TcaDomains("pseudo", "salt", "dateshift");
    var tcaConfig = new FhirPseudonymizerConfig.TCAConfig(tcaServer, domains);

    var config =
        new FhirPseudonymizerConfig(
            serviceUrl, new File("anon.yaml"), tcaConfig, Duration.ofDays(14), null);

    assertThat(config.dateShiftPreserve()).isEqualTo(DateShiftPreserve.NONE);
  }

  @Test
  void dateShiftPreserveRetainsExplicitValue() {
    var serviceUrl = new HttpClientConfig("http://localhost:1234");
    var tcaServer = new HttpClientConfig("http://localhost:5678");
    var domains = new TcaDomains("pseudo", "salt", "dateshift");
    var tcaConfig = new FhirPseudonymizerConfig.TCAConfig(tcaServer, domains);

    var config =
        new FhirPseudonymizerConfig(
            serviceUrl, new File("anon.yaml"), tcaConfig, Duration.ofDays(14), DateShiftPreserve.WEEKDAY);

    assertThat(config.dateShiftPreserve()).isEqualTo(DateShiftPreserve.WEEKDAY);
  }

  @Test
  void recordAccessors() {
    var serviceUrl = new HttpClientConfig("http://localhost:1234");
    var tcaServer = new HttpClientConfig("http://localhost:5678");
    var domains = new TcaDomains("pseudo", "salt", "dateshift");
    var tcaConfig = new FhirPseudonymizerConfig.TCAConfig(tcaServer, domains);
    var anonFile = new File("anon.yaml");
    var maxShift = Duration.ofDays(14);

    var config =
        new FhirPseudonymizerConfig(
            serviceUrl, anonFile, tcaConfig, maxShift, DateShiftPreserve.DAYTIME);

    assertThat(config.serviceUrl()).isEqualTo(serviceUrl);
    assertThat(config.anonymizationConfig()).isEqualTo(anonFile);
    assertThat(config.trustCenterAgent()).isEqualTo(tcaConfig);
    assertThat(config.maxDateShift()).isEqualTo(maxShift);
    assertThat(config.trustCenterAgent().server()).isEqualTo(tcaServer);
    assertThat(config.trustCenterAgent().domains()).isEqualTo(domains);
  }
}
