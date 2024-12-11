package care.smith.fts.cda;

import care.smith.fts.util.AgentConfiguration;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    return new ObjectMapper(new YAMLFactory())
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @Bean
  public Path projectsDirectory(@Value("${projects.directory:projects}") String directoryName) {
    return Paths.get(directoryName);
  }
}
