package care.smith.fts.tca.consent;

import static care.smith.fts.test.FhirGenerators.randomUuid;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.AbstractFhirClientIT;
import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerators;
import care.smith.fts.util.fhir.FhirUtils;
import care.smith.fts.util.tca.ConsentRequest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * Abstract base class for GICS consent provider tests.
 *
 * @param <T> The specific consent provider type
 * @param <R> The request type for the consent provider
 */
public abstract class AbstractGicsConsentProviderIT<
        T extends GicsFhirConsentedPatientsProvider, R extends ConsentRequest>
    extends AbstractFhirClientIT<T, R, Bundle> {

  protected FhirGenerator<Bundle> gicsConsentGenerator;
  protected static final Set<String> POLICIES =
      Set.of(
          "IDAT_erheben",
          "IDAT_speichern_verarbeiten",
          "MDAT_erheben",
          "MDAT_speichern_verarbeiten");
  protected static final String POLICY_SYSTEM =
      "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy";

  @BeforeEach
  void setUpGicsGenerator() throws IOException {
    gicsConsentGenerator =
        FhirGenerators.gicsResponse(randomUuid(), randomUuid(), () -> "patient-1");
  }

  /**
   * Method to be called from subclasses to initialize dependencies after Spring context load
   *
   * @param httpClientBuilder the WebClient builder
   * @param meterRegistry the metric registry
   */
  @Override
  protected void init(WebClient.Builder httpClientBuilder, MeterRegistry meterRegistry) {
    super.init(httpClientBuilder, meterRegistry);
  }

  @Override
  protected CapabilityStatement getMockCapabilityStatement() {
    var capabilities = new CapabilityStatement();
    var rest = capabilities.addRest();
    rest.addOperation().setName("allConsentsForDomain");
    rest.addOperation().setName("allConsentsForPerson");
    return capabilities;
  }

  @Override
  protected String getServerName() {
    return "gICS";
  }

  /**
   * Get the URI builder for the request.
   *
   * @return the URI builder
   */
  protected UriComponentsBuilder getUriBuilder() {
    return fromUriString("http://trustcenteragent:8080");
  }

  /**
   * Execute a paged request.
   *
   * @param request the request to execute
   * @param from the starting index
   * @param count the page size
   * @return a Mono representing the response
   */
  protected abstract Mono<Bundle> executePagedRequest(R request, int from, int count);

  /**
   * Execute a paged request with a specific client.
   *
   * @param client the client to use
   * @param request the request to execute
   * @param from the starting index
   * @param count the page size
   * @return a Mono representing the response
   */
  protected abstract Mono<Bundle> executePagedRequestWithClient(
      T client, R request, int from, int count);

  @Override
  protected Mono<Bundle> executeRequestWithClient(T specificClient, R request) {
    return executePagedRequestWithClient(specificClient, request, 0, 2);
  }

  @Override
  protected Mono<Bundle> executeRequest(R request) {
    return executePagedRequest(request, 0, 2);
  }

  /** Test that pagination works correctly. */
  @Test
  void testPaging() {
    int pageSize = 2;
    int totalEntries = 2 * pageSize;

    Bundle bundle =
        gicsConsentGenerator
            .generateResources()
            .limit(pageSize)
            .collect(toBundle())
            .setTotal(totalEntries);

    setupPagingResponses(bundle, 0, pageSize, totalEntries);

    String expectedNextLink = getExpectedNextLinkPattern(pageSize, pageSize);

    create(executePagedRequest(getDefaultRequest(), 0, pageSize))
        .assertNext(
            consentBundle ->
                assertThat(consentBundle.getLink("next").getUrl()).isEqualTo(expectedNextLink))
        .verifyComplete();

    create(executePagedRequest(getDefaultRequest(), pageSize, pageSize))
        .assertNext(consentBundle -> assertThat(consentBundle.getLink()).isEmpty())
        .verifyComplete();
  }

  /**
   * Set up mock responses for paging tests.
   *
   * @param bundle the bundle to return
   * @param offset the offset for the first page
   * @param pageSize the page size
   * @param totalEntries the total number of entries
   */
  protected abstract void setupPagingResponses(
      Bundle bundle, int offset, int pageSize, int totalEntries);

  /**
   * Get the expected next link pattern.
   *
   * @param from the starting index
   * @param count the page size
   * @return the expected next link pattern
   */
  protected abstract String getExpectedNextLinkPattern(int from, int count);

  /** Test that there is no next link on the last page. */
  @Test
  void noNextLinkOnLastPage() {
    int totalEntries = 2;
    int pageSize = 2;

    var bundle =
        Stream.generate(gicsConsentGenerator::generateString)
            .limit(totalEntries)
            .map(FhirUtils::stringToFhirBundle)
            .collect(toBundle())
            .setTotal(totalEntries);

    setupLastPageResponse(bundle, 0, pageSize);

    create(executePagedRequest(getDefaultRequest(), 2, pageSize))
        .assertNext(consentBundle -> assertThat(consentBundle.getLink("next")).isNull())
        .verifyComplete();
  }

  /**
   * Set up mock responses for the last page test.
   *
   * @param bundle the bundle to return
   * @param offset the offset
   * @param pageSize the page size
   */
  protected abstract void setupLastPageResponse(Bundle bundle, int offset, int pageSize);

  /** Test handling no consents. */
  @Test
  void noConsents() {
    int totalEntries = 0;
    int pageSize = 2;

    var bundle =
        Stream.generate(gicsConsentGenerator::generateString)
            .limit(totalEntries)
            .map(FhirUtils::stringToFhirBundle)
            .collect(toBundle())
            .setTotal(totalEntries);

    setupNoConsentsResponse(bundle, 0, pageSize);
    verifyNoConsentsResponse(0, pageSize);
  }

  /**
   * Set up mock responses for the no consents test.
   *
   * @param bundle the bundle to return
   * @param offset the offset
   * @param pageSize the page size
   */
  protected abstract void setupNoConsentsResponse(Bundle bundle, int offset, int pageSize);

  /**
   * Verify the response for the no consents test.
   *
   * @param offset the offset
   * @param pageSize the page size
   */
  protected abstract void verifyNoConsentsResponse(int offset, int pageSize);

  /** Test that empty policies yield an empty bundle. */
  @Test
  void emptyPoliciesYieldEmptyBundle() {
    R requestWithEmptyPolicies = createRequestWithEmptyPolicies();

    create(executePagedRequest(requestWithEmptyPolicies, 0, 2))
        .assertNext(
            consentBundle -> {
              assertThat(consentBundle.getEntry()).isEmpty();
            })
        .verifyComplete();
  }

  /**
   * Create a request with empty policies.
   *
   * @return the request
   */
  protected abstract R createRequestWithEmptyPolicies();
}
