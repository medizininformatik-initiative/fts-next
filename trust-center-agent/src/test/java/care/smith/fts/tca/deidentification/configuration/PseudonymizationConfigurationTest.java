package care.smith.fts.tca.deidentification.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PseudonymizationConfigurationTest {
  @Autowired private PseudonymizationConfiguration configuration;

  @Test
  void configEntriesExist() {
    assertThat(configuration.getDomain()).isNotEmpty();
    assertThat(configuration.getTransportIdTTLinSeconds()).isEqualTo(1000);
  }
}
