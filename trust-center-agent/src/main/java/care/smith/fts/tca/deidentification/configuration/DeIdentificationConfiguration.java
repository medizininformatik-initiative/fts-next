package care.smith.fts.tca.deidentification.configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;

@Configuration
@ConfigurationProperties("de-identification")
@Data
public class DeIdentificationConfiguration {
  @NotBlank String keystoreUrl;

  @Bean
  public JedisPool jedisPool() {
    return new JedisPool(keystoreUrl);
  }
}
