package care.smith.fts.rda.impl;

import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.rda.impl.DeidentifhirStepConfig.TCAConfig;
import care.smith.fts.util.HTTPClientConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
class DeidentifhirStepFactoryTest {

  @Autowired MeterRegistry meterRegistry;

  @Test
  void getConfigType() {
    assertThat(new DeidentifhirStepFactory(WebClient.builder(), meterRegistry).getConfigType())
        .isNotNull();
  }

  @Test
  void create() {
    assertThat(
            new DeidentifhirStepFactory(WebClient.builder(), meterRegistry)
                .create(
                    new Deidentificator.Config(),
                    new DeidentifhirStepConfig(
                        new TCAConfig(new HTTPClientConfig("baseUrl:1234"), "domain"),
                        ofDays(14),
                        new File("deidentifhirConfig"))))
        .isNotNull();
  }
}
