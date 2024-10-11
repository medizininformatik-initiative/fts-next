package care.smith.fts.tca.deidentification.configuration;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("de-identification.transport")
@Setter
public class TransportMappingConfiguration {
  @NotNull Long TtlSeconds;

  public Duration Ttl() {
    return Duration.ofSeconds(TtlSeconds);
  }
}
