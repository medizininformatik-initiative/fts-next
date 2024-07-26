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
import jakarta.validation.constraints.NotNull;
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

  public TCACohortSelector(TCACohortSelectorConfig config, WebClient client) {
    this.config = config;
    this.client = client;
  }

  @Override
  public Flux<ConsentedPatient> selectCohort() {
    return fetchBundle("/api/v2/cd/consented-patients")
        .expand(this::fetchNextPage)
        .doOnNext(b -> log.debug("Found {} consented patient bundles", b.getEntry().size()))
        .doOnError(e -> log.error(e.getMessage()))
        .onErrorResume(WebClientException.class, TCACohortSelector::handleError)
        .flatMap(this::extractConsentedPatients);
  }

  private Mono<Bundle> fetchBundle(String uri) {
    return client
        .post()
        .uri(uri)
        .bodyValue(constructBody(config.policies(), config.policySystem(), config.domain()))
        .headers(h -> h.setContentType(APPLICATION_JSON))
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .onStatus(r -> r.equals(HttpStatus.BAD_REQUEST), TCACohortSelector::handleBadRequest)
        .bodyToMono(Bundle.class)
        .doOnError(e -> log.error(e.getMessage()))
        .retryWhen(defaultRetryStrategy());
  }

  private Mono<Bundle> fetchNextPage(Bundle bundle) {
    return ofNullable(bundle.getLink("next"))
        .map(BundleLinkComponent::getUrl)
        .map(this::fetchBundle)
        .orElse(Mono.empty());
  }

  private Map<String, Object> constructBody(
      Set<String> policies, @NotNull String v2, String domain) {
    return Map.ofEntries(
        entry("policies", policies), entry("policySystem", v2), entry("domain", domain));
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
