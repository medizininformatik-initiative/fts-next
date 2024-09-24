package care.smith.fts.cda.impl;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.util.Map.entry;
import static java.util.Optional.ofNullable;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.util.ConsentedPatientExtractor;
import care.smith.fts.util.error.TransferProcessException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleLinkComponent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
class TCACohortSelector implements CohortSelector {
  private final TCACohortSelectorConfig config;
  private final WebClient client;
  private final MeterRegistry meterRegistry;

  public TCACohortSelector(
      TCACohortSelectorConfig config, WebClient client, MeterRegistry meterRegistry) {
    this.config = config;
    this.client = client;
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
        .onErrorResume(WebClientException.class, TCACohortSelector::handleError)
        .flatMap(this::extractConsentedPatients);
  }

  private Mono<Bundle> fetchBundle(String uri, List<String> pids) {
    log.debug("fetchBundle URL: {}", uri);
    return client
        .post()
        .uri(uri)
        .bodyValue(constructBody(config.policies(), config.policySystem(), config.domain(), pids))
        .headers(h -> h.setContentType(APPLICATION_JSON))
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .onStatus(r -> r.equals(HttpStatus.BAD_REQUEST), TCACohortSelector::handleBadRequest)
        .bodyToMono(Bundle.class)
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchBundle"));
  }

  private Mono<Bundle> fetchNextPage(Bundle bundle, List<String> pids) {
    return ofNullable(bundle.getLink("next"))
        .map(
            url -> {
              log.trace("Fetch next page from: {}", url);
              return url;
            })
        .map(BundleLinkComponent::getUrl)
        .map(uri -> fetchBundle(uri, pids))
        .orElse(Mono.empty());
  }

  private Map<String, Object> constructBody(
      Set<String> policies, @NotNull String v2, String domain, List<String> pids) {
    return Map.ofEntries(
        entry("policies", policies),
        entry("policySystem", v2),
        entry("domain", domain),
        entry("pids", pids));
  }

  private Flux<ConsentedPatient> extractConsentedPatients(Bundle outerBundle) {
    return Flux.fromStream(
        ConsentedPatientExtractor.extractConsentedPatients(
            config.patientIdentifierSystem(),
            config.policySystem(),
            outerBundle,
            config.policies()));
  }

  private static Mono<Bundle> handleError(WebClientException e) {
    return Mono.error(
        new TransferProcessException("Error communicating with trust center agent", e));
  }

  private static Mono<Throwable> handleBadRequest(ClientResponse r) {
    return r.bodyToMono(ProblemDetail.class)
        .flatMap(b -> Mono.<Throwable>error(new TransferProcessException(b.getDetail())))
        .onErrorResume(
            e -> Mono.error(new TransferProcessException("Unable to parse ProblemDetail", e)));
  }
}
