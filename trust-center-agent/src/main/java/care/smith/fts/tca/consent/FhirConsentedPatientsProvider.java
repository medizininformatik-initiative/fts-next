package care.smith.fts.tca.consent;

import static care.smith.fts.util.ConsentedPatientExtractor.hasAllPolicies;
import static care.smith.fts.util.FhirUtils.*;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.lang.Math.min;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.ConsentRequest;
import com.google.common.base.Predicates;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle.BundleLinkComponent;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/** This class provides functionalities for handling FHIR consents using an HTTP client. */
@Slf4j
public class FhirConsentedPatientsProvider implements ConsentedPatientsProvider {
  private final WebClient httpClient;
  private final PolicyHandler policyHandler;
  private final MeterRegistry meterRegistry;

  /**
   * Constructs a FhirConsentProvider with the specified parameters.
   *
   * @param httpClient the WebClient used for HTTP requests
   * @param policyHandler the handler for policy-related operations
   */
  public FhirConsentedPatientsProvider(
      WebClient httpClient, PolicyHandler policyHandler, MeterRegistry meterRegistry) {
    this.policyHandler = policyHandler;
    this.httpClient = httpClient;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<Bundle> fetch(
      ConsentRequest consentRequest, UriComponentsBuilder requestUrl, PagingParams pagingParams) {
    Set<String> policiesToCheck = policyHandler.getPoliciesToCheck(consentRequest.policies());
    if (policiesToCheck.isEmpty() || consentRequest.pids().isEmpty()) {
      return Mono.just(new Bundle());
    }
    var bundleMono =
        Fetch.fetchFromGics(httpClient, meterRegistry, consentRequest, pagingParams)
            .map(
                outerBundle ->
                    Util.filterOuterBundle(
                        consentRequest.policySystem(), policiesToCheck, outerBundle));
    if (consentRequest.pids().size() > pagingParams.sum()) {
      return bundleMono.map(
          bundle ->
              bundle.addLink(
                  Util.nextLink(requestUrl, pagingParams, "/api/v2/cd/consented-patients/fetch")));
    } else {
      return bundleMono;
    }
  }

  static class Fetch {

    private static Mono<Bundle> fetchFromGics(
        WebClient client,
        MeterRegistry meterRegistry,
        ConsentRequest consentRequest,
        PagingParams pagingParams) {
      var body = buildFetchBody(consentRequest, pagingParams);
      log.trace("Fetch from gics with URL: {}", "/$allConsentsForPerson");
      return client
          .post()
          .uri("/$allConsentsForPerson")
          .bodyValue(body)
          .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
          .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON, APPLICATION_JSON)))
          .retrieve()
          .onStatus(r -> r.equals(HttpStatus.NOT_FOUND), Util::handleGicsNotFound)
          .bodyToMono(Bundle.class)
          .retryWhen(defaultRetryStrategy(meterRegistry, "fetchFromGics"))
          .doOnError(b -> log.error("Unable to fetch consent from gICS", b));
    }

    private static Map<String, Object> buildFetchBody(
        ConsentRequest consentRequest, PagingParams pagingParams) {
      var patientIdentifier =
          getPids(consentRequest, pagingParams).stream()
              .map(
                  pid ->
                      Map.of(
                          "name",
                          "personIdentifier",
                          "valueIdentifier",
                          Map.of("system", consentRequest.policySystem(), "value", pid)));
      Map<String, Object> body =
          Map.of(
              "resourceType",
              "Parameters",
              "parameter",
              Stream.concat(
                      Stream.of(Map.of("name", "domain", "valueString", consentRequest.domain())),
                      patientIdentifier)
                  .toList());
      log.trace("body: {}", body);
      return body;
    }

    /**
     * Get patient IDs from `from` to `from + count`. If `from + count` is greater than pids.size()
     * then return patient IDs from `from` to `pids.size() -1`
     *
     * @param consentRequest
     * @param pagingParams
     * @return List of patient IDs in range
     */
    private static List<String> getPids(ConsentRequest consentRequest, PagingParams pagingParams) {
      var end = min(consentRequest.pids().size() - 1, pagingParams.sum());
      if (pagingParams.from() < end) {
        return consentRequest.pids().subList(pagingParams.from(), pagingParams.sum());
      } else {
        return List.of();
      }
    }
  }

  /**
   * Retrieves a page of consented patients for the given domain and policies, with pagination.
   *
   * @param consentRequest the set of policies to filter patients
   * @param pagingParams the starting index for pagination
   * @return a Mono emitting a Bundle of consented patients
   */
  @Override
  public Mono<Bundle> fetchAll(
      ConsentRequest consentRequest, UriComponentsBuilder requestUrl, PagingParams pagingParams) {
    Set<String> policiesToCheck = policyHandler.getPoliciesToCheck(consentRequest.policies());
    if (policiesToCheck.isEmpty()) {
      return Mono.just(new Bundle());
    }
    log.trace("Fetching consent from gics with PagingParams {}", pagingParams);
    return FetchAll.fetchPageFromGics(
            httpClient, meterRegistry, consentRequest.domain(), pagingParams)
        .map(
            outerBundle ->
                Util.filterOuterBundle(consentRequest.policySystem(), policiesToCheck, outerBundle))
        .map(bundle -> FetchAll.addNextLink(bundle, requestUrl, pagingParams));
  }

  static class FetchAll {

    private static Bundle addNextLink(
        Bundle bundle, UriComponentsBuilder requestUrl, PagingParams pagingParams) {
      if (pagingParams.sum() < bundle.getTotal()) {
        bundle.addLink(
            Util.nextLink(requestUrl, pagingParams, "/api/v2/cd/consented-patients/fetch-all"));
      }
      return bundle;
    }

    /**
     * Fetches a page of consents from the GICS system.
     *
     * @param pagingParams the PagingParams
     * @return a Mono emitting a Bundle of consents
     */
    private static Mono<Bundle> fetchPageFromGics(
        WebClient client, MeterRegistry meterRegistry, String domain, PagingParams pagingParams) {

      var body =
          Map.of(
              "resourceType",
              "Parameters",
              "parameter",
              List.of(Map.of("name", "domain", "valueString", domain)));
      var url =
          "/$allConsentsForDomain?_count=%s&_offset=%s"
              .formatted(pagingParams.count(), pagingParams.from());
      log.trace("Fetch consent page from gics with URL: {}", url);
      return client
          .post()
          .uri(url)
          .bodyValue(body)
          .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
          .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON, APPLICATION_JSON)))
          .retrieve()
          .onStatus(r -> r.equals(HttpStatus.NOT_FOUND), Util::handleGicsNotFound)
          .bodyToMono(Bundle.class)
          .retryWhen(defaultRetryStrategy(meterRegistry, "fetchAllFromGics"))
          .doOnError(b -> log.error("Unable to fetch consent from gICS", b));
    }
  }

  static class Util {

    /**
     * Filters an outer Bundle based on the provided policies. The outer Bundle contains Bundles
     * that in turn contain with Consent, Patient, and others. More info can be found here: <a
     * href="https://www.ths-greifswald.de/wp-content/uploads/tools/fhirgw/ig/2023-1-2/ImplementationGuide-markdown-Einwilligungsmanagement-Operations-allConsentsForDomain.html">...</a>
     *
     * @param policiesToCheck the set of policies to check
     * @param outerBundle the outer Bundle to filter
     * @return a filtered Bundle
     */
    private static Bundle filterOuterBundle(
        String policySystem, Set<String> policiesToCheck, Bundle outerBundle) {
      return typedResourceStream(outerBundle, Bundle.class)
          .filter(b -> hasAllPolicies(policySystem, b, policiesToCheck))
          .map(Util::filterInnerBundle)
          .collect(toBundle())
          .setTotal(outerBundle.getTotal());
    }

    /**
     * Filters an inner Bundle to include only Patient and Consent resources.
     *
     * @param b the inner Bundle to filter
     * @return a filtered Bundle
     */
    private static Bundle filterInnerBundle(Bundle b) {
      return resourceStream(b)
          .filter(Predicates.or(Patient.class::isInstance, Consent.class::isInstance))
          .collect(toBundle());
    }

    /**
     * Creates a "next" link component for pagination to the Bundle.
     *
     * <p>Constructs a URI for the next page of results using the provided request URL, paging
     * parameters, and path.
     *
     * @param requestUrl The base URL builder.
     * @param pagingParams Pagination details (sum and count).
     * @param path The path to append to the URL.
     * @return A Bundle.BundleLinkComponent with the "next" link URI.
     */
    private static BundleLinkComponent nextLink(
        UriComponentsBuilder requestUrl, PagingParams pagingParams, String path) {
      var uri =
          requestUrl
              .path(path)
              .queryParam("from", pagingParams.sum())
              .replaceQueryParam("count", pagingParams.count())
              .toUriString();
      return new BundleLinkComponent(new StringType("next"), new UriType(uri));
    }

    private static Mono<Throwable> handleGicsNotFound(ClientResponse r) {
      log.trace("response {}", r);
      return r.bodyToMono(OperationOutcome.class)
          .doOnNext(re -> log.info("{}", re))
          .flatMap(
              b -> {
                log.info("issue: {}", b.getIssueFirstRep());
                var diagnostics = b.getIssueFirstRep().getDiagnostics();
                log.error(diagnostics);
                if (diagnostics != null && diagnostics.startsWith("No consents found for domain")) {
                  return Mono.error(new UnknownDomainException(diagnostics));
                } else {
                  return Mono.error(new IllegalArgumentException());
                }
              });
    }
  }
}
