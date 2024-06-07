package care.smith.fts.tca.deidentification.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import redis.clients.jedis.JedisPool;

@SpringBootTest
class GpasFhirDeIdentificationConfigurationTest {

  @Autowired private CloseableHttpClient httpClient;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DateShiftingConfiguration config;
  @Autowired private GpasFhirDeIdentificationConfiguration gpasFhirDeIdentificationConfiguration;
  @Autowired private JedisPool jedisPool;
  @Autowired private PseudonymizationConfiguration pseudonymizationConfiguration;

  @Test
  void fhirShiftedDateProvider() {
    assertThat(gpasFhirDeIdentificationConfiguration.fhirShiftedDateProvider(config, jedisPool))
        .isNotNull();
  }

  @Test
  void fhirPseudonymProvider() {
    assertThat(
            gpasFhirDeIdentificationConfiguration.fhirPseudonymProvider(
                httpClient, objectMapper, jedisPool, pseudonymizationConfiguration))
        .isNotNull();
  }

  @Test
  void configEntriesExist() {
    assertThat(gpasFhirDeIdentificationConfiguration.getBaseUrl()).isNotEmpty();
    assertThat(gpasFhirDeIdentificationConfiguration.getAuth()).isNotNull();
  }
}
