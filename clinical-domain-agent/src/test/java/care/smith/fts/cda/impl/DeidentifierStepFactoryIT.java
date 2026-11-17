package care.smith.fts.cda.impl;

import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.cda.impl.DeidentifierStepConfig.TCAConfig;
import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.tca.TcaDomains;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.net.URISyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DeidentifierStepFactoryIT {

  @Autowired private MeterRegistry meterRegistry;
  @Autowired private WebClientFactory clientFactory;

  private DeidentifierStepFactory factory;

  @BeforeEach
  void setUp() {
    factory =
        new DeidentifierStepFactory(
            clientFactory, meterRegistry, new DefaultRetryStrategy(meterRegistry));
  }

  @Test
  void getConfigType() {
    assertThat(factory.getConfigType()).isNotNull();
  }

  @Test
  void create() throws URISyntaxException {
    assertThat(
            factory.create(
                new Deidentificator.Config(),
                config(profile("/care/smith/fts/cda/services/deidentifier/CDtoTransport.profile"))))
        .isNotNull();
  }

  @Test
  void createFailsOnProfileTheEngineRejects() throws URISyntaxException {
    var profile = profile("unknown-handler.profile");

    assertThatThrownBy(() -> factory.create(new Deidentificator.Config(), config(profile)))
        .hasMessageContaining("conditionalReferencesReplacementHandler");
  }

  private static File profile(String name) throws URISyntaxException {
    return new File(DeidentifierStepFactoryIT.class.getResource(name).toURI());
  }

  private static DeidentifierStepConfig config(File profile) {
    return new DeidentifierStepConfig(
        new TCAConfig(
            new HttpClientConfig("baseUrl:1234"), new TcaDomains("domain", "domain", "domain")),
        ofDays(14),
        profile,
        null);
  }
}
