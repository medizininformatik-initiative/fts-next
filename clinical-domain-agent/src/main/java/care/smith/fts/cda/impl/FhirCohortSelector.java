package care.smith.fts.cda.impl;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.util.ConsentedPatientExtractor;
import care.smith.fts.util.error.TransferProcessException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleLinkComponent;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Patient;
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
    var query = new StringBuilder("/Patient?_revinclude=Consent:patient");
    pids.forEach(
        pid ->
            query
                .append("&")
                .append("identifier=")
                .append(config.patientIdentifierSystem())
                .append("|")
                .append(pid));
    return query.toString();
  }

  private Mono<Bundle> fetchBundle(String uri) {
    return fhirClient
        .get()
        .uri(uri)
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .bodyToMono(Bundle.class)
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchFhirBundle"));
  }

  private Mono<Bundle> fetchNextPage(Bundle bundle) {
    return Mono.justOrEmpty(bundle.getLink("next"))
        .map(
            url -> {
              log.trace("Fetch next page from: {}", url);
              return url;
            })
        .map(BundleLinkComponent::getUrl)
        .flatMap(this::fetchBundle);
  }

  private Flux<ConsentedPatient> extractConsentedPatients(Bundle bundle) {
    return Flux.fromStream(
        ConsentedPatientExtractor.getConsentedPatients(
            config.patientIdentifierSystem(),
            config.policySystem(),
            groupPatientsAndConsents(bundle),
            config.policies()));
  }

  private static Stream<Bundle> groupPatientsAndConsents(Bundle bundle) {
    var patients = typedResourceStream(bundle, Patient.class);
    return patients
        .parallel()
        .map(
            p -> {
              var inner = new Bundle().addEntry(new BundleEntryComponent().setResource(p));
              typedResourceStream(bundle, Consent.class)
                  .filter(c -> isConsentOfPatient(p, c))
                  .forEach(c -> inner.addEntry(new BundleEntryComponent().setResource(c)));
              return inner;
            });
  }

  private static boolean isConsentOfPatient(Patient patient, Consent c) {
    var patientRefId = c.getPatient().getReferenceElement().getIdPart();
    return patientRefId.equals(patient.getIdPart());
  }

  private static Mono<Bundle> handleWebClientException(WebClientException e) {
    return Mono.error(new TransferProcessException("Error communicating with FHIR server", e));
  }
}
