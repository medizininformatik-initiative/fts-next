package care.smith.fts.cda.impl;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.cda.services.PatientIdResolver;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleLinkComponent;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class EverythingDataSelector implements DataSelector {

  private static final String RESOLVED_MSG = "Resolved patient {} to FHIR ID {}";
  private static final String FETCH_EVERYTHING_MSG =
      "fetchEverything for patient {}, FHIR ID {}, consent={}";

  private final Config common;
  private final WebClient hdsClient;
  private final PatientIdResolver pidResolver;
  private final MeterRegistry meterRegistry;
  private final int pageSize;

  public EverythingDataSelector(
      Config common,
      WebClient hdsClient,
      PatientIdResolver patientIdResolver,
      MeterRegistry meterRegistry,
      int pageSize) {
    this.common = common;
    this.hdsClient = hdsClient;
    this.pidResolver = patientIdResolver;
    this.meterRegistry = meterRegistry;
    this.pageSize = pageSize;
  }

  @Override
  public Flux<ConsentedPatientBundle> select(ConsentedPatient patient) {
    return pidResolver
        .resolve(patient)
        .doOnNext(fhirId -> log.trace(RESOLVED_MSG, patient.identifier(), fhirId.getIdPart()))
        .flatMapMany(fhirId -> fetchEverything(patient, fhirId))
        .map(b -> new ConsentedPatientBundle(b, patient))
        .doOnComplete(() -> log.trace("select for patient {} completed", patient.identifier()));
  }

  private Flux<Bundle> fetchEverything(ConsentedPatient patient, IIdType fhirId) {
    var hasConsent = !common.ignoreConsent();
    log.trace(FETCH_EVERYTHING_MSG, patient.identifier(), fhirId.getIdPart(), hasConsent);
    var uriBuilder = common.ignoreConsent() ? withoutConsent(fhirId) : withConsent(patient, fhirId);
    return fetchBundle("/Patient/{id}/$everything", uriBuilder)
        .doOnError(e -> log.error("Unable to fetch patient data from HDS: {}", e.getMessage()))
        .expand(this::fetchNextPage);
  }

  private Mono<Bundle> fetchBundle(String uri, Function<UriBuilder, URI> builder) {
    log.debug("Fetching patient data from HDS: {}", uri);
    return hdsClient
        .get()
        .uri(uri, builder)
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .bodyToMono(Bundle.class)
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchEverything"))
        .timeout(Duration.ofSeconds(30))
        .doOnNext(b -> log.trace("Fetched Bundle with {} resources", b.getEntry().size()));
  }

  private Mono<Bundle> fetchNextPage(Bundle bundle) {
    var nextLink = bundle.getLink("next");
    return Mono.justOrEmpty(nextLink)
        .switchIfEmpty(Mono.fromRunnable(() -> log.trace("fetchNextPage: no next link")))
        .doOnNext(l -> log.trace("fetchNextPage: {}", l.getUrl()))
        .map(BundleLinkComponent::getUrl)
        .flatMap(uri -> fetchBundle(uri, UriBuilder::build));
  }

  private Function<UriBuilder, URI> withoutConsent(IIdType fhirId) {
    return (uriBuilder) -> uriBuilder.queryParam("_count", pageSize).build(fhirId.getIdPart());
  }

  private Function<UriBuilder, URI> withConsent(ConsentedPatient patient, IIdType fhirId) {
    var period = patient.maxConsentedPeriod();
    if (period.isEmpty()) {
      var msg = "Patient has no consent configured, and ignoreConsent is false.";
      log.error(msg);
      throw new IllegalArgumentException(msg);
    }
    var p = period.get();
    log.trace("withConsent: patient {}, period {} to {}", patient.identifier(), p.start(), p.end());
    return (uriBuilder) ->
        uriBuilder
            .queryParam("_count", pageSize)
            .queryParam("start", formatWithSystemTZ(p.start()))
            .queryParam("end", formatWithSystemTZ(p.end()))
            .build(Map.of("id", fhirId.getIdPart()));
  }

  private static String formatWithSystemTZ(ZonedDateTime t) {
    return t.format(ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()));
  }
}
