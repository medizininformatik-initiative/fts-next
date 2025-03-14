package care.smith.fts.tca.consent;

import static care.smith.fts.tca.TtpFhirGatewayUtil.handle4xxError;
import static care.smith.fts.tca.TtpFhirGatewayUtil.handleError;
import static care.smith.fts.tca.consent.GicsFhirUtil.GICS_OPERATIONS;
import static care.smith.fts.tca.consent.GicsFhirUtil.filterOuterBundle;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;

import care.smith.fts.util.tca.ConsentFetchAllRequest;
import care.smith.fts.util.tca.ConsentFetchRequest;
import care.smith.fts.util.tca.ConsentRequest;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/** This class provides functionalities for handling FHIR consents using an HTTP client. */
@Slf4j
public class GicsFhirConsentedPatientsProvider implements ConsentedPatientsProvider {
  private final WebClient gicsClient;
  private final MeterRegistry meterRegistry;

  /**
   * Constructs a FhirConsentProvider with the specified parameters.
   *
   * @param gicsClient the WebClient used for HTTP requests
   */
  public GicsFhirConsentedPatientsProvider(WebClient gicsClient, MeterRegistry meterRegistry) {
    this.gicsClient = gicsClient;
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
    return gicsClient
        .post()
        .uri(uri -> helper.buildUri(uri, paging))
        .bodyValue(body)
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError,
            r -> handle4xxError("gICS", gicsClient, GICS_OPERATIONS, r))
        .bodyToMono(Bundle.class)
        .doOnNext(b -> log.trace("body(n: {})", b.getEntry().size()))
        .retryWhen(defaultRetryStrategy(meterRegistry, helper.requestName()))
        .onErrorResume(e -> handleError("gICS", e))
        .doOnError(b -> log.error("Unable to fetch consent from gICS", b))
        .map(outerBundle -> filterOuterBundle(req.policySystem(), req.policies(), outerBundle))
        .map(bundle -> helper.processResponse(bundle, req, requestUrl, paging));
  }
}
