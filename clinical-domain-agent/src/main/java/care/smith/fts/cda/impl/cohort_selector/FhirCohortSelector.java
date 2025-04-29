package care.smith.fts.cda.impl.cohort_selector;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.util.ConsentedPatientExtractor;
import care.smith.fts.util.error.TransferProcessException;
import care.smith.fts.util.error.fhir.FhirException;
import com.google.common.collect.Streams;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleLinkComponent;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
class FhirCohortSelector implements CohortSelector {
  private final FhirCohortSelectorConfig config;
  private final WebClient fhirClient;
  private final MeterRegistry meterRegistry;

  public FhirCohortSelector(
      FhirCohortSelectorConfig config, WebClient fhirClient, MeterRegistry meterRegistry) {
    this.config = config;
    this.fhirClient = fhirClient;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Flux<ConsentedPatient> selectCohort(@NotNull List<String> pids) {
    String query = buildFhirSearchQuery(pids);

    return fetchBundle(query)
        .expand(this::fetchNextPage)
        .timeout(Duration.ofSeconds(30))
        .doOnNext(b -> log.debug("Found {} entries in bundle", b.getEntry().size()))
        .doOnError(e -> log.error("Error fetching cohort from FHIR server: {}", e.getMessage()))
        .onErrorResume(WebClientException.class, FhirCohortSelector::handleWebClientException)
        .flatMap(this::extractConsentedPatients);
  }

  private String buildFhirSearchQuery(List<String> pids) {
    StringBuilder query = new StringBuilder("/Patient?_revinclude=Consent:patient");
    //
    //    // Add policy filter if specified
    //    if (!config.policies().isEmpty()) {
    //      String policyCodes = config.policies().stream()
    //          .map(policy -> "scope=" + config.policySystem() + "|" + policy)
    //          .collect(Collectors.joining("&"));
    //      query.append(policyCodes).append("&");
    //    }
    //
    //    // Add status filter - typically we want only active consents
    //    query.append("status=active&");
    //
    //    // Add patient filter if PIDs are provided
    //    if (!pids.isEmpty()) {
    //      String patientFilter = pids.stream()
    //          .map(pid -> "patient.identifier=" + config.patientIdentifierSystem() + "|" + pid)
    //          .collect(Collectors.joining("&"));
    //      query.append(patientFilter).append("&");
    //    }
    //
    //    // Add domain filter if domain is specified
    //    if (config.domain() != null && !config.domain().isEmpty()) {
    //      query.append("provision.actor.reference=Organization/").append(config.domain());
    //    }
    //
    //    // Remove trailing & if present
    //    if (query.charAt(query.length() - 1) == '&') {
    //      query.setLength(query.length() - 1);
    //    }

    return query.toString();
  }

  private Mono<Bundle> fetchBundle(String uri) {
    log.debug("Fetching FHIR bundle from: {}", uri);
    return fhirClient
        .get()
        .uri(uri)
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .onStatus(r -> r.equals(BAD_REQUEST), FhirCohortSelector::handleBadRequest)
        .bodyToMono(Bundle.class)
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchFhirBundle"));
  }

  private Mono<Bundle> fetchNextPage(Bundle bundle) {
    return ofNullable(bundle.getLink("next"))
        .map(
            url -> {
              log.trace("Fetch next page from: {}", url);
              return url;
            })
        .map(BundleLinkComponent::getUrl)
        .map(this::fetchBundle)
        .orElse(Mono.empty());
  }

  private Flux<ConsentedPatient> extractConsentedPatients(Bundle bundle) {

    var consents = typedResourceStream(bundle, Consent.class);
    var patients = typedResourceStream(bundle, Patient.class);
    var outerBundle = new Bundle();

    patients.forEach(
        p -> {
          var inner = new Bundle().addEntry(new BundleEntryComponent().setResource(p));

          outerBundle.addEntry(new BundleEntryComponent().setResource(inner));
        });

    Streams.zip(
            consents,
            patients,
            (c, p) ->
                new Bundle()
                    .addEntry(new BundleEntryComponent().setResource(c))
                    .addEntry(new BundleEntryComponent().setResource(p)))
        .forEach(inner -> outerBundle.addEntry(new BundleEntryComponent().setResource(inner)));

    return Flux.fromStream(
        ConsentedPatientExtractor.extractConsentedPatients(
            config.patientIdentifierSystem(),
            config.policySystem(),
            outerBundle,
            config.policies()));
  }

  private static Mono<Bundle> handleWebClientException(WebClientException e) {
    return Mono.error(new TransferProcessException("Error communicating with FHIR server", e));
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
