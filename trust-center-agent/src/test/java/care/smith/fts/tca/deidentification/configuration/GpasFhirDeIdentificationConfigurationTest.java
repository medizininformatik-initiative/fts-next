package care.smith.fts.tca.deidentification.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GpasFhirDeIdentificationConfigurationTest {

  @Autowired private GpasFhirDeIdentificationConfiguration gpasFhirDeIdentificationConfiguration;

  @Test
  void configEntriesExist() {
    assertThat(gpasFhirDeIdentificationConfiguration.getBaseUrl()).isNotEmpty();
    assertThat(gpasFhirDeIdentificationConfiguration.getAuth()).isNotNull();
  }
}
