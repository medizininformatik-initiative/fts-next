package care.smith.fts.tca.deidentification.configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConfigurationProperties("de-identification")
@Data
public class DeIdentificationConfiguration {
  private @NotBlank String keystoreUrl;

  @Bean
  public RedissonClient redisClient() {
    Config config = new Config();
    config.useSingleServer().setAddress(keystoreUrl);
    return Redisson.create(config);
  }
}
