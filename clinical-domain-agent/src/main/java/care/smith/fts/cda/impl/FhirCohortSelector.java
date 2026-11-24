package care.smith.fts.cda.impl;

import static care.smith.fts.cda.impl.PatientPage.referencedPatientId;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static care.smith.fts.util.fhir.LenientFhirReader.readLenient;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.springframework.web.util.UriComponentsBuilder.*;
import static reactor.core.publisher.Flux.defer;
import static reactor.core.publisher.Flux.fromIterable;

import care.smith.fts.api.CohortEntry;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.util.ConsentedPatientExtractor;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.error.TransferProcessException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleLinkComponent;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Slf4j
class FhirCohortSelector implements CohortSelector {
  private static final String PATIENT_REQUEST = "fetchFhirBundle";
  private static final String CONSENT_REQUEST = "fetchFhirConsents";

  private final FhirCohortSelectorConfig config;
  private final WebClient fhirClient;
  private final ConsentedPatientExtractor extractor;
  private final RetryStrategy retryStrategy;
  private final Scheduler scheduler;
  private final int cohortSelectionConcurrency;

  public FhirCohortSelector(
      FhirCohortSelectorConfig config,
      WebClient fhirClient,
      ConsentedPatientExtractor extractor,
      RetryStrategy retryStrategy,
      Scheduler scheduler,
      int cohortSelectionConcurrency) {
    this.config = config;
    this.fhirClient = fhirClient;
    this.extractor = extractor;
    this.retryStrategy = retryStrategy;
    this.scheduler = scheduler;
    this.cohortSelectionConcurrency = cohortSelectionConcurrency;
  }

  @Override
  public Flux<CohortEntry> selectCohort(List<String> identifiers) {
    log.trace("selectCohort: {} identifiers", identifiers.size());
    return defer(() -> {
      var preferring = extractor.preferring(identifiers);
      var selected = new SelectedPatients(preferring);
      return fetchPages(identifiers, preferring)
          // The next page is fetched while a page is classified; its error must not cancel the
          // classification, so the entries of the page are still emitted
          .concatMapDelayError(page -> extractCohortEntries(page, selected), false, 1)
          .doOnTerminate(selected::warnAboutIgnored)
          .doOnCancel(selected::warnAboutIgnored);
    });
  }

  private Flux<Bundle> fetchPages(List<String> identifiers, ConsentedPatientExtractor extractor) {
    return fetchBundle(b -> buildPatientSearchQuery(b, identifiers), PATIENT_REQUEST)
        .expand(page -> fetchNextPage(page, PATIENT_REQUEST))
        .concatMap(page -> withConsents(PatientPage.of(page, extractor)))
        .timeout(Duration.ofSeconds(30))
        .doOnNext(b -> log.debug("Found {} entries in bundle", b.getEntry().size()))
        .doOnError(e -> log.error("Error fetching cohort from FHIR server: {}", e.getMessage()))
        .onErrorResume(WebClientException.class, FhirCohortSelector::handleWebClientException);
  }

  private URI buildPatientSearchQuery(UriBuilder builder, List<String> identifiers) {
    builder.pathSegment("Patient");
    if (identifiers.isEmpty()) {
      builder.queryParam("_has:Consent:patient:status", "active");
    } else {
      builder.queryParam("identifier", identifierQuery(identifiers));
    }
    var uri = builder.build();
    log.trace("buildPatientSearchQuery: {}", uri);
    return uri;
  }

  /**
   * The patients of a page together with all consents of patients with their identifiers, which are
   * searched separately instead of with {@code _revinclude}, as servers may cut the resources it
   * adds to a page.
   */
  private Mono<Bundle> withConsents(PatientPage page) {
    return Mono.just(page.identifiers())
        .filter(not(List::isEmpty))
        .flatMapMany(this::fetchConsentPages)
        .collectList()
        .map(page::withConsents);
  }

  private Flux<Bundle> fetchConsentPages(List<String> identifiers) {
    return fetchBundle(b -> buildConsentSearchQuery(b, identifiers), CONSENT_REQUEST)
        .expand(page -> fetchNextPage(page, CONSENT_REQUEST));
  }

  private URI buildConsentSearchQuery(UriBuilder builder, List<String> identifiers) {
    var uri =
        builder
            .pathSegment("Consent")
            .queryParam("patient.identifier", identifierQuery(identifiers))
            .queryParam("_include", "Consent:patient")
            .build();
    log.trace("buildConsentSearchQuery: {}", uri);
    return uri;
  }

  private String identifierQuery(List<String> identifiers) {
    return identifiers.stream()
        .map(identifier -> config.patientIdentifierSystem() + "|" + identifier)
        .collect(joining(","));
  }

  private Mono<Bundle> fetchBundle(Function<UriBuilder, URI> uriBuilder, String requestName) {
    return fhirClient
        .get()
        .uri(uriBuilder)
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .toEntityFlux(readLenient(Bundle.class))
        .flatMap(response -> Mono.from(response.getBody()))
        .retryWhen(retryStrategy.forRequest(requestName))
        .doOnNext(b -> log.trace("fetchBundle: {} entries", b.getEntry().size()));
  }

  private Mono<Bundle> fetchNextPage(Bundle bundle, String requestName) {
    return Mono.justOrEmpty(bundle.getLink("next"))
        .map(BundleLinkComponent::getUrl)
        .doOnNext(url -> log.trace("Fetch next page from: {}", url))
        .flatMap(url -> fetchBundle(extractUriBuilder(url), requestName));
  }

  private static Function<UriBuilder, URI> extractUriBuilder(String url) {
    URI uri = fromUriString(url).build().toUri();
    if (uri.isAbsolute()) {
      return b -> uri;
    } else {
      return b -> b.replacePath(uri.getPath()).replaceQuery(uri.getQuery()).build();
    }
  }

  private Flux<CohortEntry> extractCohortEntries(Bundle bundle, SelectedPatients selected) {
    return groupPatientsAndConsents(bundle)
        .collectList()
        .flatMapIterable(selected::select)
        .doOnNext(p -> log.trace("extractCohortEntries: emitted {}", p.identifier()))
        .doOnComplete(() -> log.trace("extractCohortEntries completed for bundle"));
  }

  /**
   * Groups each patient in the bundle with its matching consents. The CPU-bound grouping is spread
   * across {@code cohortSelectionConcurrency} concurrent workers on the injected, dedicated {@link
   * Scheduler}, instead of the JVM-wide common ForkJoinPool that a parallel {@link
   * java.util.stream.Stream} would use.
   */
  private Flux<Bundle> groupPatientsAndConsents(Bundle bundle) {
    var patients = typedResourceStream(bundle, Patient.class).toList();
    warnAboutUnassignedConsents(bundle, patients);
    return fromIterable(patients)
        .parallel(cohortSelectionConcurrency)
        .runOn(scheduler)
        .map(p -> groupPatientWithConsents(bundle, p))
        .sequential();
  }

  private static Bundle groupPatientWithConsents(Bundle bundle, Patient p) {
    var inner = new Bundle().addEntry(new BundleEntryComponent().setResource(p));
    typedResourceStream(bundle, Consent.class)
        .filter(c -> isConsentOfPatient(p, c))
        .forEach(c -> inner.addEntry(new BundleEntryComponent().setResource(c)));
    return inner;
  }

  private static void warnAboutUnassignedConsents(Bundle bundle, List<Patient> patients) {
    var patientIds = patients.stream().map(Patient::getIdPart).collect(toSet());
    var unassigned =
        typedResourceStream(bundle, Consent.class)
            .filter(c -> referencedPatientId(c).filter(patientIds::contains).isEmpty())
            .count();
    if (unassigned > 0) {
      log.warn("Ignoring {} consents that reference no patient of the bundle", unassigned);
    }
  }

  private static boolean isConsentOfPatient(Patient patient, Consent c) {
    return referencedPatientId(c).filter(id -> id.equals(patient.getIdPart())).isPresent();
  }

  private static Mono<Bundle> handleWebClientException(WebClientException e) {
    return Mono.error(new TransferProcessException("Error communicating with FHIR server", e));
  }
}
