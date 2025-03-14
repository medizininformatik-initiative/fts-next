package care.smith.fts.tca.consent.configuration;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "consent.gics")
@Data
public class GicsConfiguration {
  private Integer pageSize = null;

  GicsFhirConfiguration fhir;

  @Bean
  int pageSize() {
    if (pageSize != null) {
      return pageSize;
    } else {
      log.warn(
          "consent.gics.fhir.defaultPageSize is deprecated. Use consent.gics.pageSize instead.");
      return fhir.getDefaultPageSize();
    }
  }
}
