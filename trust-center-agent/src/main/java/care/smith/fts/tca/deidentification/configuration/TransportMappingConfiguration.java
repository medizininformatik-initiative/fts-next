package care.smith.fts.tca.deidentification.configuration;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("de-identification.transport")
@Getter
@Setter
public class TransportMappingConfiguration {
  @NotNull Duration ttl = Duration.ofMinutes(10);
}
