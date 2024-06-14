package care.smith.fts.cda.impl;

import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.cda.DeidentificationProvider;
import care.smith.fts.cda.impl.DeidentifhirStepConfig.TCAConfig;
import care.smith.fts.util.HTTPClientConfig;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class DeidentifhirStepFactoryTest {

  @Test
  void getConfigType() {
    assertThat(new DeidentifhirStepFactory(WebClient.builder()).getConfigType()).isNotNull();
  }

  @Test
  void create() {
    assertThat(
            new DeidentifhirStepFactory(WebClient.builder())
                .create(
                    new DeidentificationProvider.Config(),
                    new DeidentifhirStepConfig(
                        new TCAConfig(new HTTPClientConfig("baseUrl:1234"), "domain"),
                        ofDays(14),
                        new File("deidentifhirConfig"),
                        new File("scraperConfig"))))
        .isNotNull();
  }
}
