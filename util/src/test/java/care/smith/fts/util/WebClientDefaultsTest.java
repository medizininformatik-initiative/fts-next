package care.smith.fts.util;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.test.StepVerifier.create;

import care.smith.fts.util.tca.DateShiftingRequest;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@WireMockTest
class WebClientDefaultsTest {

  private static final JsonMapper jsonMapper = JsonMapper.builder().build();

  // Differs from Spring's default mapper, so a missing codec registration becomes visible.
  private static final JsonMapper upperCamelCaseMapper =
      JsonMapper.builder()
          .propertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE)
          .build();

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
                      "id": "id1",
                      "dateShift": 604800.000000000
                    }
                    """,
                    200)));

    WebClient.Builder webClientBuilder = WebClient.builder();
    new WebClientDefaults(jsonMapper).customize(webClientBuilder);
    WebClient webClient = webClientBuilder.baseUrl(address).build();

    create(webClient.post().retrieve().bodyToMono(DateShiftingRequest.class))
        .assertNext(
            b -> {
              assertThat(b.id()).isEqualTo("id1");
              assertThat(b.dateShift()).isEqualTo(Duration.ofDays(7));
            })
        .verifyComplete();
  }

  @Test
  void decodesWithGivenMapper(WireMockRuntimeInfo wireMockRuntime) {
    wireMockRuntime
        .getWireMock()
        .register(get(urlEqualTo("/")).willReturn(jsonResponse("{\"Id\": \"id1\"}", 200)));

    WebClient.Builder webClientBuilder = WebClient.builder();
    new WebClientDefaults(upperCamelCaseMapper).customize(webClientBuilder);
    WebClient webClient = webClientBuilder.baseUrl(wireMockRuntime.getHttpBaseUrl()).build();

    create(webClient.get().retrieve().bodyToMono(DateShiftingRequest.class))
        .assertNext(b -> assertThat(b.id()).isEqualTo("id1"))
        .verifyComplete();
  }

  @Test
  void encodesWithGivenMapper(WireMockRuntimeInfo wireMockRuntime) {
    wireMockRuntime
        .getWireMock()
        .register(
            post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.Id", equalTo("id1")))
                .willReturn(ok()));

    WebClient.Builder webClientBuilder = WebClient.builder();
    new WebClientDefaults(upperCamelCaseMapper).customize(webClientBuilder);
    WebClient webClient = webClientBuilder.baseUrl(wireMockRuntime.getHttpBaseUrl()).build();

    var body = new DateShiftingRequest("id1", Duration.ofDays(7));
    create(webClient.post().bodyValue(body).retrieve().toBodilessEntity())
        .assertNext(r -> assertThat(r.getStatusCode().value()).isEqualTo(200))
        .verifyComplete();
  }

  @Test
  void followsTemporaryRedirect(WireMockRuntimeInfo wireMockRuntime) {
    var address = wireMockRuntime.getHttpBaseUrl();
    var wireMock = wireMockRuntime.getWireMock();
    wireMock.register(
        get(urlEqualTo("/source"))
            .willReturn(aResponse().withStatus(307).withHeader("Location", "/target")));
    wireMock.register(
        get(urlEqualTo("/target")).willReturn(aResponse().withStatus(200).withBody("ok")));

    // A redirect-following connector (as built by WebClientFactory) must pass the followed
    // response through the redirect-error filter untouched.
    var connector = new ReactorClientHttpConnector(HttpClient.create().followRedirect(true));
    WebClient.Builder webClientBuilder = WebClient.builder().clientConnector(connector);
    new WebClientDefaults(jsonMapper).customize(webClientBuilder);
    WebClient webClient = webClientBuilder.baseUrl(address).build();

    create(webClient.get().uri("/source").retrieve().bodyToMono(String.class))
        .assertNext(s -> assertThat(s).isEqualTo("ok"))
        .verifyComplete();
  }

  @Test
  void unfollowedRedirectBecomesError(WireMockRuntimeInfo wireMockRuntime) {
    var address = wireMockRuntime.getHttpBaseUrl();
    var wireMock = wireMockRuntime.getWireMock();
    // The default connector does not follow redirects, so the 3xx reaches WebClient and must
    // surface as an error instead of an empty body silently passing through (#1706).
    wireMock.register(
        get(urlEqualTo("/source"))
            .willReturn(aResponse().withStatus(307).withHeader("Location", "/target")));

    WebClient.Builder webClientBuilder = WebClient.builder();
    new WebClientDefaults(jsonMapper).customize(webClientBuilder);
    WebClient webClient = webClientBuilder.baseUrl(address).build();

    create(webClient.get().uri("/source").retrieve().bodyToMono(String.class))
        .expectErrorSatisfies(
            e ->
                assertThat(e)
                    .isInstanceOf(WebClientResponseException.class)
                    .extracting(t -> ((WebClientResponseException) t).getStatusCode().value())
                    .isEqualTo(307))
        .verify();
  }

  @AfterEach
  void tearDown(WireMockRuntimeInfo wireMockRuntime) {
    var wireMock = wireMockRuntime.getWireMock();
    wireMock.resetMappings();
  }
}
