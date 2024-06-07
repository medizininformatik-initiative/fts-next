package care.smith.fts.tca.deidentification.configuration;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "de-identification.date-shifting")
@Data
public class DateShiftingConfiguration {
  private Long shiftByDays;

  public Long getShiftByMillis() {
    return shiftByDays * 24 * 60 * 60 * 1000;
  }

}
