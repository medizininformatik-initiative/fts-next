package care.smith.fts.cda.impl;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.util.GicsConsentedPatientExtractor;
import care.smith.fts.util.error.TransferProcessException;
import care.smith.fts.util.error.fhir.FhirException;
import com.google.common.collect.ImmutableMap;
import io.micrometer.core.instrument.MeterRegistry;
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
class TCACohortSelector implements CohortSelector {
  private final TCACohortSelectorConfig config;
  private final WebClient tcaClient;
  private final MeterRegistry meterRegistry;

  public TCACohortSelector(
      TCACohortSelectorConfig config, WebClient tcaClient, MeterRegistry meterRegistry) {
    this.config = config;
    this.tcaClient = tcaClient;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Flux<ConsentedPatient> selectCohort(List<String> pids) {
    var url =
        pids.isEmpty()
            ? "/api/v2/cd/consented-patients/fetch-all"
            : "/api/v2/cd/consented-patients/fetch";

    return fetchBundle(url, pids)
        .expand(bundle -> fetchNextPage(bundle, pids))
        .timeout(Duration.ofSeconds(30))
        .doOnNext(b -> log.debug("Found {} consented patient bundles", b.getEntry().size()))
        .doOnError(e -> log.error("Error fetching cohort: {}", e.getMessage()))
        .onErrorResume(WebClientException.class, TCACohortSelector::handleWebClientException)
        .flatMap(this::extractConsentedPatients);
  }

  private Mono<Bundle> fetchBundle(String uri, List<String> pids) {
    log.debug("fetchBundle URL: {}", uri);
    return tcaClient
        .post()
        .uri(uri)
        .bodyValue(constructBody(config, pids))
        .headers(h -> h.setContentType(APPLICATION_JSON))
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .onStatus(r -> r.equals(BAD_REQUEST), TCACohortSelector::handleBadRequest)
        .bodyToMono(Bundle.class)
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchBundle"));
  }

  private Mono<Bundle> fetchNextPage(Bundle bundle, List<String> pids) {
    return Mono.justOrEmpty(bundle.getLink("next"))
        .map(BundleLinkComponent::getUrl)
        .doOnNext(url -> log.trace("Fetch next page from: {}", url))
        .flatMap(uri -> fetchBundle(uri, pids));
  }

  private static Map<String, Object> constructBody(
      TCACohortSelectorConfig config, List<String> pids) {
    var body =
        ImmutableMap.<String, Object>builder()
            .put("policies", config.policies())
            .put("policySystem", config.policySystem())
            .put("domain", config.domain());
    if (!pids.isEmpty()) {
      body = body.put("patientIdentifierSystem", config.patientIdentifierSystem());
      body = body.put("pids", pids);
    }
    return body.build();
  }

  private Flux<ConsentedPatient> extractConsentedPatients(Bundle outerBundle) {
    return Flux.fromStream(
        GicsConsentedPatientExtractor.extractConsentedPatients(
            config.patientIdentifierSystem(),
            config.policySystem(),
            outerBundle,
            config.policies()));
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
