package care.smith.fts.tca.deidentification.configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConfigurationProperties("de-identification")
@Setter
public class DeIdentificationConfiguration {
  @NotBlank String keystoreUrl;

  @Bean
  public RedissonClient redisClient() {
    Config config = new Config();
    config.useSingleServer().setAddress(keystoreUrl);
    return Redisson.create(config);
  }

  @Bean
  public RedissonReactiveClient reactiveRedisClient(RedissonClient redisClient) {
    return redisClient.reactive();
  }
}
