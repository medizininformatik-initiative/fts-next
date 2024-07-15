package care.smith.fts.util;

import static ca.uhn.fhir.context.FhirContext.forR4;
import static care.smith.fts.util.FhirUtils.toBundle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static reactor.test.StepVerifier.create;

import ca.uhn.fhir.context.FhirContext;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(classes = {FhirCodecConfiguration.class, WebClientFhirCodecTest.Config.class})
@ExtendWith(MockServerExtension.class)
class WebClientFhirCodecTest {

  @Autowired WebClient.Builder client;

  @Test
  void decodeResponse(MockServerClient mockServer) {
    var address = "http://localhost:%d".formatted(mockServer.getPort());

    Bundle bundle = Stream.of(new Patient().setId("patient-094857")).collect(toBundle());
    mockServer
        .when(request().withMethod("GET").withPath("/"))
        .respond(response().withBody(FhirUtils.fhirResourceToString(bundle), APPLICATION_JSON));
    WebClient webClient = client.baseUrl(address).build();

    create(webClient.get().retrieve().bodyToMono(Bundle.class))
        .assertNext(b -> b.equalsDeep(bundle))
        .verifyComplete();
  }

  @Test
  void encodeRequest(MockServerClient mockServer) {
    var address = "http://localhost:%d".formatted(mockServer.getPort());

    Bundle bundle = Stream.of(new Patient().setId("patient-094857")).collect(toBundle());
    mockServer
        .when(request().withMethod("POST").withPath("/"))
        .respond(response().withStatusCode(201));
    WebClient webClient = client.baseUrl(address).build();

    var response =
        webClient
            .post()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(bundle)
            .retrieve()
            .toBodilessEntity();
    create(response)
        .assertNext(e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CREATED))
        .verifyComplete();
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }

  @TestConfiguration
  static class Config {
    @Bean
    FhirContext fhirContext() {
      return forR4();
    }

    @Bean
    WebClient.Builder builder(WebClientCustomizer customizer) {
      WebClient.Builder builder = WebClient.builder();
      customizer.customize(builder);
      return builder;
    }
  }
}
