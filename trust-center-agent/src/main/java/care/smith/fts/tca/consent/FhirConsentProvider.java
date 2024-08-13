package care.smith.fts.tca.consent;

import static care.smith.fts.util.ConsentedPatientExtractor.hasAllPolicies;
import static care.smith.fts.util.FhirUtils.*;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import care.smith.fts.util.error.UnknownDomainException;
import com.google.common.base.Predicates;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/** This class provides functionalities for handling FHIR consents using an HTTP client. */
@Slf4j
@Component
public class FhirConsentProvider implements ConsentProvider {
  private final int defaultPageSize;
  private final WebClient httpClient;
  private final PolicyHandler policyHandler;

  /**
   * Constructs a FhirConsentProvider with the specified parameters.
   *
   * @param httpClient the WebClient used for HTTP requests
   * @param policyHandler the handler for policy-related operations
   * @param defaultPageSize the default page size for paginated results
   */
  public FhirConsentProvider(
      @Qualifier("gicsFhirHttpClient") WebClient httpClient,
      PolicyHandler policyHandler,
      int defaultPageSize) {
    this.policyHandler = policyHandler;
    this.httpClient = httpClient;
    this.defaultPageSize = defaultPageSize;
  }

  /**
   * Retrieves the first page (with defaultPageSize) of consented patients for the given domain and
   * policies.
   *
   * @param domain the domain to search for consented patients
   * @param policies the set of policies to filter patients
   * @return a Mono emitting a Bundle of consented patients
   */
  @Override
  public Mono<Bundle> consentedPatientsPage(
      String domain, String policySystem, Set<String> policies, UriComponentsBuilder requestUrl) {
    return consentedPatientsPage(domain, policySystem, policies, requestUrl, 0, defaultPageSize);
  }

  /**
   * Retrieves a page of consented patients for the given domain and policies, with pagination.
   *
   * @param domain the domain to search for consented patients
   * @param policies the set of policies to filter patients
   * @param from the starting index for pagination
   * @param count the number of patients to retrieve
   * @return a Mono emitting a Bundle of consented patients
   */
  @Override
  public Mono<Bundle> consentedPatientsPage(
      String domain,
      String policySystem,
      Set<String> policies,
      UriComponentsBuilder requestUrl,
      int from,
      int count) {

    Set<String> policiesToCheck = policyHandler.getPoliciesToCheck(policies);
    if (policiesToCheck.isEmpty()) {
      return Mono.just(new Bundle());
    }

    return Mono.fromCallable(() -> new PagingParams(from, count))
        .doOnNext(b -> log.trace("Fetching consent from gics with PagingParams {}", b))
        .flatMap(
            p ->
                fetchConsentedPatientsFromGics(policySystem, policiesToCheck, domain, p)
                    .map(bundle -> addNextLink(bundle, requestUrl, p)));
  }

  private Bundle addNextLink(
      Bundle bundle, UriComponentsBuilder requestUrl, PagingParams pagingParams) {
    if (pagingParams.sum() < bundle.getTotal()) {
      bundle.addLink(nextLink(requestUrl, pagingParams));
    }
    return bundle;
  }

  private static Bundle.BundleLinkComponent nextLink(
      UriComponentsBuilder requestUrl, PagingParams pagingParams) {
    var uri =
        requestUrl
            .replaceQueryParam("from", pagingParams.sum())
            .replaceQueryParam("count", pagingParams.count())
            .toUriString();
    return new Bundle.BundleLinkComponent(new StringType("next"), new UriType(uri));
  }

  /**
   * Fetches a page of consented patients from the GICS system, filtered by policies.
   *
   * @param policiesToCheck the set of policies to check
   * @param pagingParams the PagingParams
   * @return a Mono emitting a filtered Bundle of consented patients
   */
  private Mono<Bundle> fetchConsentedPatientsFromGics(
      String policySystem, Set<String> policiesToCheck, String domain, PagingParams pagingParams) {
    return fetchConsentPageFromGics(domain, pagingParams)
        .map(outerBundle -> filterOuterBundle(policySystem, policiesToCheck, outerBundle));
  }

  /**
   * Filters an outer Bundle based on the provided policies. The outer Bundle contains Bundles that
   * in turn contain with Consent, Patient, and others. More info can be found here: <a
   * href="https://www.ths-greifswald.de/wp-content/uploads/tools/fhirgw/ig/2023-1-2/ImplementationGuide-markdown-Einwilligungsmanagement-Operations-allConsentsForDomain.html">...</a>
   *
   * @param policiesToCheck the set of policies to check
   * @param outerBundle the outer Bundle to filter
   * @return a filtered Bundle
   */
  private Bundle filterOuterBundle(
      String policySystem, Set<String> policiesToCheck, Bundle outerBundle) {
    return typedResourceStream(outerBundle, Bundle.class)
        .filter(b -> hasAllPolicies(policySystem, b, policiesToCheck))
        .map(FhirConsentProvider::filterInnerBundle)
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
   * Fetches a page of consents from the GICS system.
   *
   * @param pagingParams the PagingParams
   * @return a Mono emitting a Bundle of consents
   */
  private Mono<Bundle> fetchConsentPageFromGics(String domain, PagingParams pagingParams) {
    var body =
        Map.of(
            "resourceType",
            "Parameters",
            "parameter",
            List.of(Map.of("name", "domain", "valueString", domain)));
    var url =
        "/$allConsentsForDomain?_count=%s&_offset=%s"
            .formatted(pagingParams.count, pagingParams.from());
    log.trace("Fetch consent page from gics with URL: {}", url);
    return httpClient
        .post()
        .uri(url)
        .bodyValue(body)
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON, APPLICATION_JSON)))
        .retrieve()
        .onStatus(r -> r.equals(HttpStatus.NOT_FOUND), FhirConsentProvider::handleGicsNotFound)
        .bodyToMono(Bundle.class)
        .retryWhen(defaultRetryStrategy())
        .doOnNext(b -> log.trace("Consent fetched, {} bundle entries", b.getEntry().size()))
        .doOnError(b -> log.error("Error fetching consent", b));
  }

  private static Mono<Throwable> handleGicsNotFound(ClientResponse r) {
    return r.bodyToMono(OperationOutcome.class)
        .flatMap(
            b -> {
              var diagnostics = b.getIssueFirstRep().getDiagnostics();
              log.error(diagnostics);
              if (diagnostics != null && diagnostics.startsWith("No consents found for domain")) {
                return Mono.error(new UnknownDomainException(diagnostics));
              } else {
                return Mono.error(new UnknownError());
              }
            });
  }

  record PagingParams(int from, int count) {

    PagingParams {
      if (from < 0 || count < 0) {
        throw new IllegalArgumentException("from and count must be non-negative");
      } else if (Integer.MAX_VALUE - count < from) {
        throw new IllegalArgumentException(
            "from + count must be smaller than %s".formatted(Integer.MAX_VALUE));
      }
    }

    int sum() {
      return from + count;
    }
  }
}
