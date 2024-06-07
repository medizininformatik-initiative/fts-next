package care.smith.fts.tca.deidentification.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DateShiftingConfigurationTest {

  @Autowired DateShiftingConfiguration config;

  @Test
  void configEntriesExist() {
    assertThat(config.getShiftByDays()).isNotNull();
  }

  @Test
  void computationOfShiftByMillisIsCorrect() {
    config.setShiftByDays(10L);
    assertThat(config.getShiftByDays()).isEqualTo(10);
    assertThat(config.getShiftByMillis()).isEqualTo(10 * 24 * 60 * 60 * 1000);
  }
}
