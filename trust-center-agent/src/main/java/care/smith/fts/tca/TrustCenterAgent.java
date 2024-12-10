package care.smith.fts.tca;

import static java.time.Duration.ofSeconds;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.util.CustomErrorHandler;
import care.smith.fts.util.FhirCodecConfiguration;
import care.smith.fts.util.MetricsConfig;
import care.smith.fts.util.WebClientDefaults;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.auth.HttpServerAuthConfig;
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

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
@Import({
  WebClientDefaults.class,
  FhirCodecConfiguration.class,
  MetricsConfig.class,
  HttpServerAuthConfig.class,
  CustomErrorHandler.class,
  WebClientFactory.class,
})
public class TrustCenterAgent {

  public static void main(String... args) {
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
