package care.smith.fts.util;

import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.net.http.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
class WebClientDefaultsTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private static final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(ofSeconds(10)).build();

  @Test
  void customizeWebClientAndDecodeToMono(WireMockRuntimeInfo wireMockRuntime) {
    var address = wireMockRuntime.getHttpBaseUrl();
    var wireMock = wireMockRuntime.getWireMock();
    wireMock.register(
        post(urlEqualTo("/"))
            .willReturn(
                jsonResponse(
                    """
                    {
                      "id": "patient",
                      "patientIdentifierSystem": "system",
                      "consentedPolicies": {
                        "policies":{
                          "a":[
                            {"start": -23220777600.000000000, "end": -23220604800.000000000}
                          ]
                        }
                      }
                    }
                    """,
                    200)));

    WebClient.Builder webClientBuilder = WebClient.builder();
    new WebClientDefaults(httpClient, objectMapper).customize(webClientBuilder);
    WebClient webClient = webClientBuilder.baseUrl(address).build();

    create(webClient.post().retrieve().bodyToMono(ConsentedPatient.class))
        .assertNext(
            b -> {
              assertThat(b.id()).isEqualTo("patient");
              assertThat(b.patientIdentifierSystem()).isEqualTo("system");
              assertThat(b.consentedPolicies().policyNames()).isNotEmpty();
            })
        .verifyComplete();
  }

  @AfterEach
  void tearDown(WireMockRuntimeInfo wireMockRuntime) {
    var wireMock = wireMockRuntime.getWireMock();
    wireMock.resetMappings();
  }
}
