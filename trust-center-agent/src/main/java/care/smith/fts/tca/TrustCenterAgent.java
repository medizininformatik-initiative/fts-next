package care.smith.fts.tca;

import ca.uhn.fhir.context.FhirContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
public class TrustCenterAgent {

  public static void main(String... args) {
    SpringApplication.run(TrustCenterAgent.class, args);
  }

  @Bean
  public FhirContext fhirContext() {
    return FhirContext.forR4();
  }
}
