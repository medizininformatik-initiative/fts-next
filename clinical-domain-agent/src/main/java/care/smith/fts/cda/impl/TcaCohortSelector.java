package care.smith.fts.cda.impl;

import static care.smith.fts.util.ConsentedPatientExtractor.getPatientIdentifier;
import static care.smith.fts.util.ConsentedPatientExtractor.processConsentedPatients;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.error.TransferProcessException;
import care.smith.fts.util.error.fhir.FhirException;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleLinkComponent;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
class TcaCohortSelector implements CohortSelector {
  private final TcaCohortSelectorConfig config;
  private final WebClient tcaClient;
  private final RetryStrategy retryStrategy;

  public TcaCohortSelector(
      TcaCohortSelectorConfig config, WebClient tcaClient, RetryStrategy retryStrategy) {
    this.config = config;
    this.tcaClient = tcaClient;
    this.retryStrategy = retryStrategy;
  }

  private String getGicsIdentifierSystem() {
    return "https://ths-greifswald.de/fhir/gics/identifiers/" + config.signerIdType();
  }

  @Override
  public Flux<ConsentedPatient> selectCohort(List<String> identifiers) {
    var url =
        identifiers.isEmpty()
            ? "/api/v2/cd/consented-patients/fetch-all"
            : "/api/v2/cd/consented-patients/fetch";
    log.trace("selectCohort: url={}, {} identifiers", url, identifiers.size());

    return fetchBundle(url, identifiers)
        .expand(bundle -> fetchNextPage(bundle, identifiers))
        .timeout(Duration.ofSeconds(30))
        .doOnNext(b -> log.debug("Found {} consented patient bundles", b.getEntry().size()))
        .doOnError(e -> log.error("Error fetching cohort: {}", e.getMessage()))
        .onErrorResume(WebClientException.class, TcaCohortSelector::handleWebClientException)
        .flatMap(this::extractConsentedPatients);
  }

  private Mono<Bundle> fetchBundle(String uri, List<String> identifiers) {
    log.debug("fetchBundle URL: {}", uri);
    return tcaClient
        .post()
        .uri(uri)
        .bodyValue(constructBody(config, identifiers))
        .headers(h -> h.setContentType(APPLICATION_JSON))
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .onStatus(r -> r.equals(BAD_REQUEST), TcaCohortSelector::handleBadRequest)
        .bodyToMono(Bundle.class)
        .retryWhen(retryStrategy.forRequest("fetchBundle"));
  }

  private Mono<Bundle> fetchNextPage(Bundle bundle, List<String> identifiers) {
    return Mono.justOrEmpty(bundle.getLink("next"))
        .map(BundleLinkComponent::getUrl)
        .doOnNext(url -> log.trace("Fetch next page from: {}", url))
        .flatMap(uri -> fetchBundle(uri, identifiers));
  }

  private Map<String, Object> constructBody(
      TcaCohortSelectorConfig config, List<String> identifiers) {
    var body =
        ImmutableMap.<String, Object>builder()
            .put("policies", config.policies())
            .put("policySystem", config.policySystem())
            .put("domain", config.domain());
    if (!identifiers.isEmpty()) {
      body = body.put("patientIdentifierSystem", getGicsIdentifierSystem());
      body = body.put("identifiers", identifiers);
    }
    var result = body.build();
    log.trace(
        "constructBody: policies={}, identifiers={}", config.policies().size(), identifiers.size());
    return result;
  }

  private Flux<ConsentedPatient> extractConsentedPatients(Bundle outerBundle) {
    var resources = typedResourceStream(outerBundle, Bundle.class);
    return Flux.fromStream(
            processConsentedPatients(
                config.patientIdentifierSystem(),
                config.policySystem(),
                resources,
                config.policies(),
                b -> getPatientIdentifier(getGicsIdentifierSystem(), b)))
        .doOnNext(p -> log.trace("extractConsentedPatients: emitted {}", p.identifier()))
        .doOnComplete(() -> log.trace("extractConsentedPatients completed for bundle"));
  }

  private static Mono<Bundle> handleWebClientException(WebClientException e) {
    return Mono.error(
        new TransferProcessException("Error communicating with trust center agent", e));
  }

  private static Mono<Throwable> handleBadRequest(ClientResponse r) {
    return r.bodyToMono(OperationOutcome.class)
        .onErrorResume(
            e -> {
              log.error("Failed to parse error response", e);
              return Mono.just(
                  new FhirException(BAD_REQUEST, e.getMessage()).getOperationOutcome());
            })
        .flatMap(
            outcome ->
                Mono.error(
                    new TransferProcessException(outcome.getIssueFirstRep().getDiagnostics())));
  }
}
