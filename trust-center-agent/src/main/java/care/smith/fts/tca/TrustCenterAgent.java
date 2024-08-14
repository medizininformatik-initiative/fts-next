package care.smith.fts.tca;

import static java.time.Duration.ofSeconds;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.util.FhirCodecConfiguration;
import care.smith.fts.util.WebClientDefaults;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import reactor.tools.agent.ReactorDebugAgent;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
@Import({WebClientDefaults.class, FhirCodecConfiguration.class})
public class TrustCenterAgent {

  public static void main(String... args) {
    ReactorDebugAgent.init();
    SpringApplication.run(TrustCenterAgent.class, args);
  }

  @Bean
  public FhirContext fhirContext() {
    return FhirContext.forR4();
  }

  @Bean
  public HttpClient httpClient() {
    return HttpClient.newBuilder().connectTimeout(ofSeconds(10)).build();
  }

  @Bean
  @Primary
  public ObjectMapper defaultObjectMapper() {
    return new ObjectMapper().registerModule(new JavaTimeModule());
  }
}
