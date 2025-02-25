package care.smith.fts.tca.consent;

import static care.smith.fts.test.FhirGenerators.fromList;
import static care.smith.fts.test.FhirGenerators.randomUuid;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import care.smith.fts.test.FhirGenerators;
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.tca.ConsentFetchRequest;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SpringBootTest
@WireMockTest
@Import(TestWebClientFactory.class)
class GicsFhirConsentedPatientsProviderFetchIT
    extends AbstractGicsConsentProviderIT<GicsFhirConsentedPatientsProvider, ConsentFetchRequest> {

  @Autowired WebClient.Builder httpClientBuilder;

  @Autowired MeterRegistry meterRegistry;

  @MockitoBean
  RedissonClient redisClient; // We need to mock the redisClient otherwise the tests won't start

  @BeforeEach
  void setUpDependencies() {
    init(httpClientBuilder, meterRegistry);
  }

  private static final String PATIENT_IDENTIFIER_SYSTEM =
      "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym";

  private static final ConsentFetchRequest CONSENT_FETCH_REQUEST =
      new ConsentFetchRequest(
          "MII",
          POLICIES,
          POLICY_SYSTEM,
          PATIENT_IDENTIFIER_SYSTEM,
          List.of("id1", "id2", "id3", "id4"));

  private static final String REQUEST_BODY1 =
      """
      {
        "resourceType": "Parameters",
        "parameter": [
          {"name": "domain", "valueString": "MII"},
          {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym", "value": "id1"}},
          {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym", "value": "id2"}}
      ]}
      """;

  private static final String REQUEST_BODY2 =
      """
      {
        "resourceType": "Parameters",
        "parameter": [
          {"name": "domain", "valueString": "MII"},
          {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym", "value": "id3"}},
          {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym", "value": "id4"}}
      ]}
      """;

  @BeforeEach
  @Override
  void setUpGicsGenerator() throws IOException {
    gicsConsentGenerator =
        FhirGenerators.gicsResponse(randomUuid(), fromList(List.of("id1", "id2", "id3", "id4")));
  }

  @Override
  protected GicsFhirConsentedPatientsProvider createClient(String baseUrl) {
    return new GicsFhirConsentedPatientsProvider(
        httpClientBuilder.baseUrl(baseUrl).build(), meterRegistry);
  }

  @Override
  protected MappingBuilder getRequestMappingBuilder() {
    return post(urlPathEqualTo("/$allConsentsForPerson"))
        .withRequestBody(equalToJson(REQUEST_BODY1));
  }

  @Override
  protected ConsentFetchRequest getDefaultRequest() {
    return CONSENT_FETCH_REQUEST;
  }

  @Override
  protected Mono<Bundle> executePagedRequest(ConsentFetchRequest request, int from, int count) {
    return client.fetch(request, getUriBuilder(), new PagingParams(from, count));
  }

  @Override
  protected Mono<Bundle> executePagedRequestWithClient(
      GicsFhirConsentedPatientsProvider client, ConsentFetchRequest request, int from, int count) {
    return client.fetch(request, getUriBuilder(), new PagingParams(from, count));
  }

  @Override
  protected void setupPagingResponses(Bundle bundle, int offset, int pageSize, int totalEntries) {
    wireMock.register(
        post("/$allConsentsForPerson")
            .withRequestBody(equalToJson(REQUEST_BODY1))
            .willReturn(fhirResponse(bundle)));

    wireMock.register(
        post("/$allConsentsForPerson")
            .withRequestBody(equalToJson(REQUEST_BODY2))
            .willReturn(fhirResponse(bundle)));
  }

  @Override
  protected String getExpectedNextLinkPattern(int from, int count) {
    return "http://trustcenteragent:8080/api/v2/cd/consented-patients/fetch?from=%s&count=%s"
        .formatted(from, count);
  }

  @Override
  protected void setupLastPageResponse(Bundle bundle, int offset, int pageSize) {
    wireMock.register(
        post(urlPathEqualTo("/$allConsentsForPerson"))
            .withRequestBody(equalToJson(REQUEST_BODY2))
            .willReturn(fhirResponse(bundle)));
  }

  @Override
  protected void setupNoConsentsResponse(Bundle bundle, int offset, int pageSize) {
    wireMock.register(getRequestMappingBuilder().willReturn(fhirResponse(bundle)));
  }

  @Override
  protected void verifyNoConsentsResponse(int offset, int pageSize) {
    String expectedNextLink =
        "http://trustcenteragent:8080/api/v2/cd/consented-patients/fetch?from=%s&count=%s"
            .formatted(pageSize, pageSize);

    create(
            client.fetch(
                CONSENT_FETCH_REQUEST,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(offset, pageSize)))
        .assertNext(
            consentBundle -> {
              assertThat(consentBundle.getEntry()).isEmpty();
              assertThat(consentBundle.getLink("next").getUrl()).isEqualTo(expectedNextLink);
            })
        .verifyComplete();
  }

  @Override
  protected ConsentFetchRequest createRequestWithEmptyPolicies() {
    return new ConsentFetchRequest(
        "MII",
        Set.of(),
        POLICY_SYSTEM,
        PATIENT_IDENTIFIER_SYSTEM,
        List.of("id1", "id2", "id3", "id4"));
  }

  @Test
  void emptyPidsYieldEmptyBundle() {
    var consentRequest =
        new ConsentFetchRequest(
            "MII", POLICIES, POLICY_SYSTEM, PATIENT_IDENTIFIER_SYSTEM, List.of());
    create(
            client.fetch(
                consentRequest,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, 2)))
        .assertNext(
            consentBundle -> {
              assertThat(consentBundle.getEntry()).isEmpty();
            })
        .verifyComplete();
  }
}
