package care.smith.fts.tca.consent.configuration;

import java.util.HashSet;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "consent")
@Data
public class ConsentProviderConfiguration {

  String policySystem;
  String patientIdentifierSystem;
  HashSet<String> defaultPolicies;

  @Bean
  String policySystem() {
    return policySystem;
  }

  @Bean
  String patientIdentifierSystem() {
    return patientIdentifierSystem;
  }

  @Bean
  HashSet<String> defaultPolicies() {
    return defaultPolicies;
  }
}
