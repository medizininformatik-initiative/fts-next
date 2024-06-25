package care.smith.fts.rda.impl;

import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.rda.DeidentificationProvider;
import care.smith.fts.rda.impl.DeidentifhirStepConfig.TCAConfig;
import care.smith.fts.util.HTTPClientConfig;
import java.io.File;
import org.junit.jupiter.api.Test;

class DeidentifhirStepFactoryTest {

  @Test
  void getConfigType() {
    assertThat(new DeidentifhirStepFactory().getConfigType()).isNotNull();
  }

  @Test
  void create() {
    assertThat(
            new DeidentifhirStepFactory()
                .create(
                    new DeidentificationProvider.Config(),
                    new DeidentifhirStepConfig(
                        new TCAConfig(new HTTPClientConfig("baseUrl:1234"), "domain"),
                        ofDays(14),
                        new File("deidentifhirConfig"))))
        .isNotNull();
  }
}