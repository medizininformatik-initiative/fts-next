package care.smith.fts.util;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.http.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockServerExtension.class)
class WebClientDefaultsTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private static final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(ofSeconds(10)).build();

  @Test
  void customizeWebClientAndDecodeToMono(MockServerClient mockServer) {
    var address = "http://localhost:%d".formatted(mockServer.getPort());
    mockServer
        .when(request().withMethod("POST").withPath("/"))
        .respond(
            response()
                .withBody(
                    json(
                        """
                                {"id":"patient","consentedPolicies":{"policies":{"a":[
                                      {"start":-23220777600.000000000,"end":-23220604800.000000000}]}}}
                                """)));
    WebClient.Builder webClientBuilder = WebClient.builder();
    new WebClientDefaults(httpClient, objectMapper).customize(webClientBuilder);
    WebClient webClient = webClientBuilder.baseUrl(address).build();

    create(webClient.post().retrieve().bodyToMono(ConsentedPatient.class))
        .assertNext(
            b -> {
              assertThat(b.id()).isEqualTo("patient");
              assertThat(b.consentedPolicies().policyNames()).isNotEmpty();
            })
        .verifyComplete();
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
