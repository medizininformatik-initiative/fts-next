package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import care.smith.fts.api.DeidentificationProvider;
import care.smith.fts.util.auth.HTTPClientAuthMethod;
import java.io.File;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeidentifhirDeidentificationProviderFactoryTest {

  @Test
  void getConfigType() {
    assertThat(new DeidentifhirDeidentificationProviderFactory().getConfigType()).isNotNull();
  }

  @Test
  void create() {
    assertThat(
            new DeidentifhirDeidentificationProviderFactory()
                .create(
                    new DeidentificationProvider.Config(),
                    new DeidentifhirDeidentificationProviderFactory.Config(
                        "baseUrl:1234",
                        HTTPClientAuthMethod.AuthMethod.NONE,
                        "domain",
                        Duration.ofDays(14),
                        new File("deidentifhirConfig"),
                        new File("scraperConfig"))))
        .isNotNull();
  }
}
