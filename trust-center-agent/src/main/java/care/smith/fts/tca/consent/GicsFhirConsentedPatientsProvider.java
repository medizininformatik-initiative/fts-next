package care.smith.fts.tca.consent;

import static care.smith.fts.tca.consent.GicsFhirUtil.filterOuterBundle;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import care.smith.fts.util.error.fhir.FhirException;
import care.smith.fts.util.error.fhir.FhirUnknownDomainException;
import care.smith.fts.util.error.fhir.NoFhirServerException;
import care.smith.fts.util.tca.ConsentFetchAllRequest;
import care.smith.fts.util.tca.ConsentFetchRequest;
import care.smith.fts.util.tca.ConsentRequest;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
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
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON, APPLICATION_JSON)))
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, this::handle4xxError)
        .bodyToMono(Bundle.class)
        .doOnNext(b -> log.trace("body(n: {})", b.getEntry().size()))
        .retryWhen(defaultRetryStrategy(meterRegistry, helper.requestName()))
        .onErrorResume(
            e -> {
              if (e.getCause() instanceof WebClientRequestException) {
                return Mono.error(new NoFhirServerException("No connection to gICS server"));
              } else if (e.getCause() instanceof WebClientResponseException) {
                return Mono.error(new FhirException(INTERNAL_SERVER_ERROR, e.getMessage()));
              } else {
                return Mono.error(e);
              }
            })
        .doOnError(b -> log.error("Unable to fetch consent from gICS", b))
        .map(outerBundle -> filterOuterBundle(req.policySystem(), req.policies(), outerBundle))
        .map(bundle -> helper.processResponse(bundle, req, requestUrl, paging));
  }

  private Mono<Throwable> handle4xxError(ClientResponse r) {
    log.trace("response headers: {}", r.headers().asHttpHeaders());
    if (Set.of(400, 401, 404, 422).contains(r.statusCode().value())) {
      log.debug("Status code: {}", r.statusCode().value());
      return r.bodyToMono(OperationOutcome.class)
          .onErrorResume(
              e -> {
                log.error("Cannot parse OperationOutcome expected from gICS", e);
                return Mono.error(
                    new NoFhirServerException("Cannot parse OperationOutcome received from gICS"));
              })
          .flatMap(
              b -> {
                var diagnostics = b.getIssueFirstRep().getDiagnostics();
                log.error(diagnostics);
                if (r.statusCode() == NOT_FOUND) {
                  return Mono.error(new FhirUnknownDomainException(b));
                } else {
                  return Mono.error(new FhirException(BAD_REQUEST, b));
                }
              });
    } else {
      return Mono.error(new NoFhirServerException("Unexpected error connecting to gICS"));
    }
  }
}
