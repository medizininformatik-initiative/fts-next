package care.smith.fts.cda;

import care.smith.fts.util.AgentConfiguration;
import care.smith.fts.util.TransferProcessObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

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
    return TransferProcessObjectMapper.create();
  }
}
