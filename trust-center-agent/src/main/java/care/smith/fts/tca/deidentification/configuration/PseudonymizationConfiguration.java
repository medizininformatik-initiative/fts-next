package care.smith.fts.tca.deidentification.configuration;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("de-identification.pseudonymization")
@Data
public class PseudonymizationConfiguration {
  @NotNull Long transportIdTTLinSeconds;
}
