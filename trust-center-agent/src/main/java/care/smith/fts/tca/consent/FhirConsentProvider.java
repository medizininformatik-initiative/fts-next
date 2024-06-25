package care.smith.fts.tca.consent;

import static care.smith.fts.util.ConsentedPatientExtractor.hasAllPolicies;
import static care.smith.fts.util.FhirUtils.*;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.google.common.base.Predicates;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
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
    log.trace("Fetching consent from gics");
    return fetchConsentedPatientsFromGics(policySystem, policiesToCheck, domain, from, count)
        .map(bundle -> addNextLink(bundle, requestUrl, from, count));
  }

  private Bundle addNextLink(Bundle bundle, UriComponentsBuilder requestUrl, int from, int count) {
    if (bundle.getTotal() > from + count) {
      bundle.addLink(nextLink(requestUrl, from, count));
    }
    return bundle;
  }

  private static Bundle.BundleLinkComponent nextLink(
      UriComponentsBuilder requestUrl, int from, int count) {
    var uri =
        requestUrl
            .replaceQueryParam("from", from + count)
            .replaceQueryParam("count", count)
            .toUriString();
    return new Bundle.BundleLinkComponent(new StringType("next"), new UriType(uri));
  }

  /**
   * Fetches a page of consented patients from the GICS system, filtered by policies.
   *
   * @param policiesToCheck the set of policies to check
   * @param from the starting index for pagination
   * @param count the number of patients to retrieve
   * @return a Mono emitting a filtered Bundle of consented patients
   */
  private Mono<Bundle> fetchConsentedPatientsFromGics(
      String policySystem, Set<String> policiesToCheck, String domain, int from, int count) {
    return fetchConsentPageFromGics(domain, from, count)
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
   * @param from the starting index for pagination
   * @param count the number of consents to retrieve
   * @return a Mono emitting a Bundle of consents
   */
  private Mono<Bundle> fetchConsentPageFromGics(String domain, int from, int count) {
    int to = from + count;
    var body =
        Map.of(
            "resourceType",
            "Parameters",
            "parameter",
            List.of(Map.of("name", "domain", "valueString", domain)));
    String formatted = "/$allConsentsForDomain?_count=%s&_offset=%s".formatted(to, from);
    log.info(formatted);
    return httpClient
        .post()
        .uri(formatted)
        .bodyValue(body)
        .headers(h -> h.setContentType(APPLICATION_JSON))
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON, APPLICATION_JSON)))
        .retrieve()
        .bodyToMono(Bundle.class);
  }
}
