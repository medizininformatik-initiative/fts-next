package care.smith.fts.tca.deidentification.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
class GpasFhirDeIdentificationConfigurationTest {

  @Autowired private GpasFhirDeIdentificationConfiguration gpasFhirDeIdentificationConfiguration;

  @MockBean
  RedissonClient redisClient; // We need to mock the redisClient otherwise the tests won't start

  @Test
  void configEntriesExist() {
    assertThat(gpasFhirDeIdentificationConfiguration.getBaseUrl()).isNotEmpty();
    assertThat(gpasFhirDeIdentificationConfiguration.getAuth()).isNotNull();
  }
}
