package care.smith.fts.tca.consent;

import static care.smith.fts.tca.consent.GicsFhirUtil.filterOuterBundle;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.ConsentFetchAllRequest;
import care.smith.fts.util.tca.ConsentFetchRequest;
import care.smith.fts.util.tca.ConsentRequest;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/** This class provides functionalities for handling FHIR consents using an HTTP client. */
@Slf4j
public class FhirConsentedPatientsProvider implements ConsentedPatientsProvider {
  private final WebClient client;
  private final MeterRegistry meterRegistry;

  /**
   * Constructs a FhirConsentProvider with the specified parameters.
   *
   * @param client the WebClient used for HTTP requests
   */
  public FhirConsentedPatientsProvider(
      WebClient client,  MeterRegistry meterRegistry) {
    this.client = client;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<Bundle> fetch(
      ConsentFetchRequest req, UriComponentsBuilder requestUrl, PagingParams paging) {
    if (req.policies().isEmpty() || req.pids().isEmpty()) {
      return Mono.just(new Bundle());
    }

    return doFetch(req, requestUrl, paging, new FetchAllConsentsForPersons());
  }

  /**
   * Retrieves a page of consented patients for the given domain and policies, with pagination.
   *
   * @param req the set of policies to filter patients
   * @param paging the starting index for pagination
   * @return a Mono emitting a Bundle of consented patients
   */
  @Override
  public Mono<Bundle> fetchAll(
      ConsentFetchAllRequest req, UriComponentsBuilder requestUrl, PagingParams paging) {
    if (req.policies().isEmpty()) {
      return Mono.just(new Bundle());
    }

    log.trace("Fetching consent from gics with PagingParams {}", paging);
    return doFetch(req, requestUrl, paging, new FetchAllConsentsForDomain());
  }

  private <C extends ConsentRequest> Mono<Bundle> doFetch(
      C req,
      UriComponentsBuilder requestUrl,
      PagingParams paging,
      GicsFhirRequestHelper<C> helper) {
    var body = helper.buildBody(req, paging);
    return client
        .post()
        .uri(uri -> helper.buildUri(uri, paging))
        .bodyValue(body)
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON, APPLICATION_JSON)))
        .retrieve()
        .onStatus(
            r -> r.equals(HttpStatus.NOT_FOUND), FhirConsentedPatientsProvider::handleGicsNotFound)
        .bodyToMono(Bundle.class)
        .doOnNext(b -> log.trace("body(n: {})", b.getEntry().size()))
        .retryWhen(defaultRetryStrategy(meterRegistry, helper.requestName()))
        .doOnError(b -> log.error("Unable to fetch consent from gICS", b))
        .map(outerBundle -> filterOuterBundle(req.policySystem(), req.policies(), outerBundle))
        .map(bundle -> helper.processResponse(bundle, req, requestUrl, paging));
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
