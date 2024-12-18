package care.smith.fts.util;

import static ca.uhn.fhir.context.FhirContext.forR4;
import static care.smith.fts.util.FhirUtils.fhirResourceToString;
import static care.smith.fts.util.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.test.StepVerifier.create;

import ca.uhn.fhir.context.FhirContext;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(classes = {FhirCodecConfiguration.class, WebClientFhirCodecTest.Config.class})
@WireMockTest
class WebClientFhirCodecTest {

  @Autowired WebClient.Builder client;

  @Test
  void decodeResponse(WireMockRuntimeInfo wireMockRuntime) {
    var address = wireMockRuntime.getHttpBaseUrl();
    var wireMock = wireMockRuntime.getWireMock();

    Bundle bundle = Stream.of(new Patient().setId("patient-094857")).collect(toBundle());
    wireMock.register(
        get(urlEqualTo("/")).willReturn(jsonResponse(fhirResourceToString(bundle), 200)));
    WebClient webClient = client.baseUrl(address).build();

    create(webClient.get().retrieve().bodyToMono(Bundle.class))
        .assertNext(b -> b.equalsDeep(bundle))
        .verifyComplete();
  }

  @Test
  void encodeRequest(WireMockRuntimeInfo wireMockRuntime) {
    var address = wireMockRuntime.getHttpBaseUrl();
    var wireMock = wireMockRuntime.getWireMock();

    Bundle bundle = Stream.of(new Patient().setId("patient-094857")).collect(toBundle());
    wireMock.register(post(urlEqualTo("/")).willReturn(created()));
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
  void tearDown(WireMockRuntimeInfo wireMockRuntime) {
    var wireMock = wireMockRuntime.getWireMock();
    wireMock.resetMappings();
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
