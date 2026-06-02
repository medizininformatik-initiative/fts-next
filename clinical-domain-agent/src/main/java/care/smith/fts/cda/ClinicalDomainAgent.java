package care.smith.fts.cda;

import care.smith.fts.util.AgentConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
@Import(AgentConfiguration.class)
public class ClinicalDomainAgent {

  public static void main(String... args) {
    SpringApplication.run(ClinicalDomainAgent.class, args);
  }

  @Bean
  public ObjectMapper transferProcessObjectMapper() {
    return new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());
  }
}
