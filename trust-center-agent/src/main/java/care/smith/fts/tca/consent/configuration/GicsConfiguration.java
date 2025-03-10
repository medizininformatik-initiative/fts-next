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
  private int DEFAULT_PAGE_SIZE = 50;
  private Integer pageSize = null;

  GicsFhirConfiguration fhir;

  @Bean("gicsPageSize")
  int pageSize() {
    if (pageSize != null) {
      return pageSize;
    } else if (fhir.defaultPageSize() != null) {
      return fhir.defaultPageSize();
    } else {
      return DEFAULT_PAGE_SIZE;
    }
  }
}
