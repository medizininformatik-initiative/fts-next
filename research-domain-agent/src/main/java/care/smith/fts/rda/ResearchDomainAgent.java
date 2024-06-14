package care.smith.fts.rda;

import static com.google.common.base.Strings.isNullOrEmpty;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ForkJoinPool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
public class ResearchDomainAgent {

  public static void main(String... args) {
    SpringApplication.run(ResearchDomainAgent.class, args);
  }

  @Bean
  public FhirContext fhirContext() {
    return FhirContext.forR4();
  }

  @Bean
  IGenericClient client(FhirContext fhir) {
    return fhir.newRestfulGenericClient("http://localhost");
  }

  @Bean
  public ForkJoinPool transferProcessPool(
      @Value("${transferProcess.parallelism:4}") int parallelism) {
    return new ForkJoinPool(parallelism);
  }

  @Bean
  @Primary
  public ObjectMapper defaultObjectMapper() {
    return new ObjectMapper().registerModule(new JavaTimeModule());
  }

  @Bean
  public ObjectMapper transferProcessObjectMapper() {
    return new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @Bean
  public Path projectsDirectory(@Value("${projects.directory:}") String directoryName) {
    if (isNullOrEmpty(directoryName)) {
      return Paths.get("projects");
    } else {
      return Paths.get(directoryName);
    }
  }
}
