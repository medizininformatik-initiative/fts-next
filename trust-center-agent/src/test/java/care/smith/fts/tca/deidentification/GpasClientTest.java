package care.smith.fts.tca.deidentification;

import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.util.fhir.FhirUtils.fhirResourceToString;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.deidentification.configuration.GpasDeIdentificationConfiguration;
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.error.fhir.FhirException;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ContentTypes;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
@WireMockTest
@Import(TestWebClientFactory.class)
class GpasClientTest {

  @Autowired WebClient.Builder httpClientBuilder;
  @Autowired MeterRegistry meterRegistry;
  @MockitoBean RedissonClient redisClient;

  private GpasClient gpasClient;
  private WireMock wireMock;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime) {
    var address = wireMockRuntime.getHttpBaseUrl();
    wireMock = wireMockRuntime.getWireMock();
    var config = new GpasDeIdentificationConfiguration();
    gpasClient = new GpasClient(httpClientBuilder.baseUrl(address).build(), meterRegistry, config);
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }

  @Nested
  class FetchOrCreatePseudonymsTests {

    @Test
    void returnsEmptyMapForEmptyInput() {
      create(gpasClient.fetchOrCreatePseudonyms("domain", Set.of()))
          .assertNext(result -> assertThat(result).isEmpty())
          .verifyComplete();
    }

    @Test
    void returnsPseudonymsForMultipleIds() {
      wireMock.register(
          post("/$pseudonymizeAllowCreate")
              .willReturn(
                  fhirResponse(
                      """
                      {
                        "resourceType": "Parameters",
                        "parameter": [
                          {
                            "name": "pseudonym",
                            "part": [
                              {"name": "original", "valueIdentifier": {"value": "id1"}},
                              {"name": "target", "valueIdentifier": {"value": "domain"}},
                              {"name": "pseudonym", "valueIdentifier": {"value": "pseudo1"}}
                            ]
                          },
                          {
                            "name": "pseudonym",
                            "part": [
                              {"name": "original", "valueIdentifier": {"value": "id2"}},
                              {"name": "target", "valueIdentifier": {"value": "domain"}},
                              {"name": "pseudonym", "valueIdentifier": {"value": "pseudo2"}}
                            ]
                          }
                        ]
                      }
                      """)));

      create(gpasClient.fetchOrCreatePseudonyms("domain", Set.of("id1", "id2")))
          .assertNext(
              result -> {
                assertThat(result).hasSize(2);
                assertThat(result).containsEntry("id1", "pseudo1");
                assertThat(result).containsEntry("id2", "pseudo2");
              })
          .verifyComplete();
    }

    @Test
    void handles4xxErrorWithUnknownDomain() {
      wireMock.register(
          post("/$pseudonymizeAllowCreate")
              .willReturn(
                  fhirResponse(
                      """
                      {"resourceType": "OperationOutcome",
                       "issue": [{"severity": "error", "code": "processing",
                                  "diagnostics": "Unknown domain"}]}
                      """,
                      BAD_REQUEST)));
      wireMock.register(
          get(urlPathEqualTo("/metadata"))
              .withQueryParam("_elements", equalTo("rest.operation"))
              .willReturn(
                  status(OK.value())
                      .withHeader(ContentTypes.CONTENT_TYPE, APPLICATION_FHIR_JSON)
                      .withBody(fhirResourceToString(gpasMockCapabilityStatement()))));

      create(gpasClient.fetchOrCreatePseudonyms("unknown-domain", Set.of("id1")))
          .expectError(FhirException.class)
          .verify();
    }

    @Test
    void handles4xxErrorWithOperationOutcome() {
      wireMock.register(
          post("/$pseudonymizeAllowCreate")
              .willReturn(
                  fhirResponse(
                      """
                      {"resourceType": "OperationOutcome",
                       "issue": [{"severity": "error", "code": "processing",
                                  "diagnostics": "Some error message"}]}
                      """,
                      BAD_REQUEST)));
      wireMock.register(
          get(urlPathEqualTo("/metadata"))
              .withQueryParam("_elements", equalTo("rest.operation"))
              .willReturn(
                  status(OK.value())
                      .withHeader(ContentTypes.CONTENT_TYPE, APPLICATION_FHIR_JSON)
                      .withBody(fhirResourceToString(gpasMockCapabilityStatement()))));

      create(gpasClient.fetchOrCreatePseudonyms("domain", Set.of("id1", "id2")))
          .expectError(FhirException.class)
          .verify();
    }

    private CapabilityStatement gpasMockCapabilityStatement() {
      var capabilities = new CapabilityStatement();
      var rest = capabilities.addRest();
      rest.addOperation().setName("pseudonymizeAllowCreate");
      return capabilities;
    }
  }
}
