package care.smith.fts.cda.impl;

import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.cda.impl.DeidentifhirStepConfig.TCAConfig;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.tca.TCADomains;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DeidentifhirStepFactoryIT {

  @Autowired private MeterRegistry meterRegistry;
  @Autowired private WebClientFactory clientFactory;

  private DeidentifhirStepFactory factory;

  @BeforeEach
  void setUp() {
    factory = new DeidentifhirStepFactory(clientFactory, meterRegistry);
  }

  @Test
  void getConfigType() {
    assertThat(factory.getConfigType()).isNotNull();
  }

  @Test
  void create() {
    assertThat(
            factory.create(
                new Deidentificator.Config(),
                new DeidentifhirStepConfig(
                    new TCAConfig(
                        new HttpClientConfig("baseUrl:1234"),
                        new TCADomains("domain", "domain", "domain")),
                    ofDays(14),
                    new File("deidentifhirConfig"),
                    new File("scraperConfig"))))
        .isNotNull();
  }
}
