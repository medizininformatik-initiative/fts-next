package care.smith.fts.tca.consent;

import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.lang.String.valueOf;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;
import static reactor.test.StepVerifier.create;

import io.micrometer.core.instrument.MeterRegistry;

import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.tca.ConsentFetchAllRequest;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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

import java.util.Set;

@SpringBootTest
@WireMockTest
@Import(TestWebClientFactory.class)
class GicsFhirConsentedPatientsProviderFetchAllIT
    extends AbstractGicsConsentProviderIT<
        GicsFhirConsentedPatientsProvider, ConsentFetchAllRequest> {

  @Autowired WebClient.Builder httpClientBuilder;

  @Autowired MeterRegistry meterRegistry;

  @MockitoBean
  RedissonClient redisClient; // We need to mock the redisClient otherwise the tests won't start

  @BeforeEach
  void setUpDependencies() {
    init(httpClientBuilder, meterRegistry);
  }

  private static final ConsentFetchAllRequest CONSENT_FETCH_ALL_REQUEST =
      new ConsentFetchAllRequest("MII", POLICIES, POLICY_SYSTEM);

  private static final String REQUEST_BODY =
      """
      {
       "resourceType": "Parameters",
       "parameter": [{"name": "domain", "valueString": "MII"}]
      }
      """;

  @Override
  protected GicsFhirConsentedPatientsProvider createClient(String baseUrl) {
    return new GicsFhirConsentedPatientsProvider(
        httpClientBuilder.baseUrl(baseUrl).build(), meterRegistry);
  }

  @Override
  protected MappingBuilder getRequestMappingBuilder() {
    return post(urlPathEqualTo("/$allConsentsForDomain"))
        .withRequestBody(equalToJson(REQUEST_BODY));
  }

  @Override
  protected ConsentFetchAllRequest getDefaultRequest() {
    return CONSENT_FETCH_ALL_REQUEST;
  }

  @Override
  protected Mono<Bundle> executePagedRequest(ConsentFetchAllRequest request, int from, int count) {
    return client.fetchAll(request, getUriBuilder(), new PagingParams(from, count));
  }

  @Override
  protected Mono<Bundle> executePagedRequestWithClient(
      GicsFhirConsentedPatientsProvider client,
      ConsentFetchAllRequest request,
      int from,
      int count) {
    return client.fetchAll(request, getUriBuilder(), new PagingParams(from, count));
  }

  @Override
  protected void setupPagingResponses(Bundle bundle, int offset, int pageSize, int totalEntries) {
    wireMock.register(
        getRequestMappingBuilder()
            .withQueryParams(
                ofEntries(
                    entry("_offset", equalTo("0")), entry("_count", equalTo(valueOf(pageSize)))))
            .willReturn(fhirResponse(bundle)));

    wireMock.register(
        post(urlPathEqualTo("/$allConsentsForDomain"))
            .withRequestBody(equalToJson(REQUEST_BODY))
            .withQueryParams(
                ofEntries(
                    entry("_offset", equalTo(valueOf(pageSize))),
                    entry("_count", equalTo(valueOf(pageSize)))))
            .willReturn(fhirResponse(bundle)));
  }

  @Override
  protected String getExpectedNextLinkPattern(int from, int count) {
    return "http://trustcenteragent:8080/api/v2/cd/consented-patients/fetch-all?from=%s&count=%s"
        .formatted(from, count);
  }

  @Override
  protected void setupLastPageResponse(Bundle bundle, int offset, int pageSize) {
    wireMock.register(
        getRequestMappingBuilder()
            .withQueryParams(
                ofEntries(
                    entry("_offset", equalTo("2")), entry("_count", equalTo(valueOf(pageSize)))))
            .willReturn(fhirResponse(bundle)));
  }

  @Override
  protected void setupNoConsentsResponse(Bundle bundle, int offset, int pageSize) {
    wireMock.register(
        getRequestMappingBuilder()
            .withQueryParams(
                ofEntries(
                    entry("_offset", equalTo("0")), entry("_count", equalTo(valueOf(pageSize)))))
            .willReturn(fhirResponse(bundle)));
  }

  @Override
  protected void verifyNoConsentsResponse(int offset, int pageSize) {
    create(
            client.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
                fromUriString("http://trustcenteragent:1234"),
                new PagingParams(offset, pageSize)))
        .assertNext(
            consentBundle -> {
              assertThat(consentBundle.getEntry()).isEmpty();
              assertThat(consentBundle.getLink("next")).isNull();
            })
        .verifyComplete();
  }

  @Override
  protected ConsentFetchAllRequest createRequestWithEmptyPolicies() {
    return new ConsentFetchAllRequest("MII", Set.of(), POLICY_SYSTEM);
  }
}
