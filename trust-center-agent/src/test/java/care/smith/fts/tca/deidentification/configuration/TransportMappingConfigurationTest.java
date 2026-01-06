package care.smith.fts.tca.deidentification.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
class TransportMappingConfigurationTest {
  @Autowired private TransportMappingConfiguration configuration;

  @MockitoBean RedissonClient redisClient;
  @MockitoBean RedissonReactiveClient reactiveRedisClient;

  @Test
  void configEntriesExist() {
    assertThat(configuration.getTtl()).isEqualTo(Duration.ofMinutes(10));
  }
}
