package care.smith.fts.util;

import static ca.uhn.fhir.context.FhirContext.forR4;
import static care.smith.fts.util.FhirUtils.fhirResourceToString;
import static care.smith.fts.util.FhirUtils.resourceStream;
import static care.smith.fts.util.FhirUtils.toBundle;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.reactive.server.WebTestClient.bindToServer;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.util.auth.HttpServerAuthConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Slf4j
@SpringBootTest(
    classes = {
      FhirCodecConfiguration.class,
      WebServerFhirCodecTest.Config.class,
      HttpServerAuthConfig.class
    },
    webEnvironment = RANDOM_PORT)
public class WebServerFhirCodecTest {

  @LocalServerPort private int port;

  private WebTestClient testClient;
  private WebClient webClient;

  @BeforeEach
  void setUp(@Autowired WebClient.Builder builder) {
    testClient = bindToServer().baseUrl("http://localhost:%d".formatted(port)).build();
    webClient = builder.baseUrl("http://localhost:%d".formatted(port)).build();
  }

  @ParameterizedTest
  @ValueSource(strings = {"simple", "mono", "response"})
  void patient(String path) {
    Resource resource = new Patient().setId("patient-081429");
    postRequest(path, resource);
  }

  @ParameterizedTest
  @ValueSource(strings = {"simple", "mono", "response"})
  void bundle(String path) {
    Resource resource = Stream.of(new Patient().setId("patient-081429")).collect(toBundle());
    postRequest(path, resource);
  }

  @Test
  void patientFluxToFlux() {
    StepVerifier.create(getRequest("/stream/Patient").bodyToFlux(Patient.class))
        .assertNext(
            p -> {
              log.info("First patient");
              assertThat(p.getIdPart()).isEqualTo("0");
            })
        .assertNext(
            p -> {
              log.info("Second patient");
              assertThat(p.getIdPart()).isEqualTo("1");
            })
        .verifyComplete();
  }

  @Test
  void patientFluxToMono() {
    StepVerifier.create(getRequest("/stream/Patient").bodyToMono(Patient.class))
        .expectError()
        .verify();
  }

  @TestConfiguration
  @RestController
  public static class Controller {
    @GetMapping(
        path = "/stream/Patient",
        produces = {APPLICATION_JSON_VALUE, APPLICATION_FHIR_JSON_VALUE})
    Flux<Resource> fluxPatient() {
      return Flux.fromStream(Stream.of(new Patient().setId("0"), new Patient().setId("1")))
          .delayElements(Duration.ofSeconds(1))
          .doOnNext(r -> log.info("Issuing patient"));
    }

    @PostMapping(
        path = "/simple/Patient",
        consumes = {APPLICATION_JSON_VALUE},
        produces = {APPLICATION_JSON_VALUE})
    Patient monoPatient(@RequestBody Patient body) {
      return body;
    }

    @PostMapping(
        path = "/simple/Bundle",
        consumes = {APPLICATION_JSON_VALUE},
        produces = {APPLICATION_JSON_VALUE})
    Bundle simplePatient(@RequestBody Bundle body) {
      return resourceStream(body).collect(toBundle());
    }

    @PostMapping(
        path = "/mono/Patient",
        consumes = {APPLICATION_JSON_VALUE},
        produces = {APPLICATION_JSON_VALUE})
    Mono<Patient> monoPatient(@RequestBody Mono<Patient> body) {
      return body.log();
    }

    @PostMapping(
        path = "/mono/Bundle",
        consumes = {APPLICATION_JSON_VALUE},
        produces = {APPLICATION_JSON_VALUE})
    Mono<Bundle> simple(@RequestBody Mono<Bundle> body) {
      return body.map(b -> resourceStream(b).collect(toBundle())).log();
    }

    @PostMapping(
        path = "/response/Patient",
        consumes = {APPLICATION_JSON_VALUE, APPLICATION_FHIR_JSON_VALUE},
        produces = {APPLICATION_JSON_VALUE, APPLICATION_FHIR_JSON_VALUE})
    Mono<ResponseEntity<Patient>> responsePatient(@RequestBody Mono<Patient> body) {
      return body.log().map(m -> new ResponseEntity<>(m, HttpStatus.OK));
    }

    @PostMapping(
        path = "/response/Bundle",
        consumes = {APPLICATION_JSON_VALUE, APPLICATION_FHIR_JSON_VALUE},
        produces = {APPLICATION_JSON_VALUE, APPLICATION_FHIR_JSON_VALUE})
    Mono<ResponseEntity<Bundle>> responseBundle(@RequestBody Mono<Bundle> body) {
      return body.map(b -> resourceStream(b).collect(toBundle()))
          .log()
          .map(m -> new ResponseEntity<>(m, HttpStatus.OK));
    }
  }

  @SpringBootApplication
  @Import({WebServerFhirCodecTest.Controller.class, FhirCodecConfiguration.class})
  public static class Config {}

  private ResponseSpec getRequest(String path) {
    return webClient
        .get()
        .uri(uri -> uri.path(path).build())
        .accept(MediaType.APPLICATION_JSON)
        .retrieve();
  }

  private void postRequest(String parameterType, Resource resource) {
    String patientAsString = fhirResourceToString(resource);

    testClient
        .post()
        .uri(
            uri ->
                uri.pathSegment(parameterType)
                    .pathSegment(resource.getResourceType().name())
                    .build())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(patientAsString)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(patientAsString);
  }
}
