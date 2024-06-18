package care.smith.fts.tca.deidentification.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;
import redis.clients.jedis.JedisPool;

@SpringBootTest
class GpasFhirDeIdentificationConfigurationTest {

  @Autowired private WebClient httpClient;
  @Autowired private GpasFhirDeIdentificationConfiguration gpasFhirDeIdentificationConfiguration;
  @Autowired private JedisPool jedisPool;
  @Autowired private PseudonymizationConfiguration pseudonymizationConfiguration;

  @Test
  void fhirShiftedDateProvider() {
    assertThat(gpasFhirDeIdentificationConfiguration.fhirShiftedDateProvider(jedisPool))
        .isNotNull();
  }

  @Test
  void configEntriesExist() {
    assertThat(gpasFhirDeIdentificationConfiguration.getBaseUrl()).isNotEmpty();
    assertThat(gpasFhirDeIdentificationConfiguration.getAuth()).isNotNull();
  }
}
