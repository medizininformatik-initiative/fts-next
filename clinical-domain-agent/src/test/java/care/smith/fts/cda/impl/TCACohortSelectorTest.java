package care.smith.fts.cda.impl;

import static care.smith.fts.util.auth.HTTPClientAuthMethod.AuthMethod.NONE;
import static java.lang.String.join;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockserver.matchers.MatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import care.smith.fts.api.CohortSelector;
import care.smith.fts.util.HTTPClientConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ExtendWith(MockServerExtension.class)
class TCACohortSelectorTest {

  private static final List<String> POLICIES =
      List.of(
          "IDAT_erheben",
          "IDAT_speichern_verarbeiten",
          "MDAT_erheben",
          "MDAT_speichern_verarbeiten");

  private static final String SAMPLE_RESPONSE =
      """
      [{"id":"SmithIT16","consentedPolicies":{"policies":{"MDAT_speichern_verarbeiten":[{"start":1690617836.985000000,"end":2637389036.985000000}],"MDAT_erheben":[{"start":1690617836.985000000,"end":2637389036.985000000}],"IDAT_erheben":[{"start":1690617836.985000000,"end":2637389036.985000000}],"IDAT_speichern_verarbeiten":[{"start":1690617836.985000000,"end":2637389036.985000000}]}}}]
      """;

  @Autowired ObjectMapper objectMapper;
  @Autowired HttpClientBuilder httpClient;

  private CohortSelector cohortSelector;

  @BeforeEach
  void setUp(MockServerClient mockServer) {
    var address = "http://localhost:%d".formatted(mockServer.getPort());
    var server = new HTTPClientConfig(address, NONE);
    var config = new TCACohortSelectorConfig(server, POLICIES, "MII");
    this.cohortSelector = new TCACohortSelector(config, objectMapper, httpClient.build());
  }

  @Test
  void jsonResponseParsed(MockServerClient mockServer) {
    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/api/v1/cd/consent-request")
                .withBody(
                    json(
                        """
                        {"policies": ["%s"], "domain": "MII"}
                        """
                            .formatted(join("\", \"", POLICIES)),
                        ONLY_MATCHING_FIELDS)))
        .respond(response().withBody(SAMPLE_RESPONSE, APPLICATION_JSON));

    assertThat(cohortSelector.selectCohort()).isNotEmpty();
  }

  @Test
  void jsonResponseInvalid(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST").withPath("/api/v1/cd/consent-request"))
        .respond(response().withBody("{}", APPLICATION_JSON));

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> cohortSelector.selectCohort())
        .withMessageContaining("parse response");
  }

  @Test
  void requestThrows(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST").withPath("/api/v1/cd/consent-request"))
        .respond(response().withStatusCode(400));

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> cohortSelector.selectCohort())
        .withMessageContaining("error")
        .withMessageContaining("400");
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
