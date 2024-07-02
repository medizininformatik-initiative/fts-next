package care.smith.fts.util;

import static ca.uhn.fhir.context.FhirContext.forR4;
import static care.smith.fts.util.FhirUtils.fhirResourceToString;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static java.time.Duration.ofSeconds;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import ca.uhn.fhir.context.FhirContext;
import java.net.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest(classes = WebServerFhirCodecTest.Config.class, webEnvironment = RANDOM_PORT)
public class WebServerFhirCodecTest {

  @LocalServerPort private int port;

  private WebTestClient webClient;

  @BeforeEach
  void setUp() {
    webClient = WebTestClient.bindToServer().baseUrl("http://localhost:%d".formatted(port)).build();
  }

  @Test
  void simpleReturnsSamePatient() {
    Resource patient = new Patient().setLanguage("Klingon").setId("patient-081429");

    String patientAsString = fhirResourceToString(patient);

    webClient
        .post()
        .uri("/simple")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(patientAsString)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(patientAsString);
  }

  @TestConfiguration
  @RestController
  public static class Controller {
    @PostMapping(
        path = "/simple",
        consumes = {APPLICATION_JSON_VALUE},
        produces = {APPLICATION_JSON_VALUE})
    Mono<Patient> simple(@RequestBody Mono<Patient> patient) {
      return patient.log();
    }

    @PostMapping(
        path = "/response",
        consumes = {APPLICATION_JSON_VALUE, APPLICATION_FHIR_JSON_VALUE},
        produces = {APPLICATION_JSON_VALUE, APPLICATION_FHIR_JSON_VALUE})
    Mono<ResponseEntity<Patient>> response(@RequestBody Mono<Patient> patient) {
      return patient.log().map(m -> new ResponseEntity<>(m, HttpStatus.OK));
    }
  }

  @SpringBootApplication
  @Import({WebServerFhirCodecTest.Controller.class, FhirCodecConfiguration.class})
  public static class Config {
    @Bean
    FhirContext fhirContext() {
      return forR4();
    }

    @Bean
    public HttpClient httpClient() {
      return HttpClient.newBuilder().connectTimeout(ofSeconds(10)).build();
    }
  }
}
