package care.smith.fts.cda.impl;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.cda.services.PatientIdResolver;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class EverythingDataSelector implements DataSelector {
  private final Config common;
  private final WebClient client;
  private final PatientIdResolver pidResolver;
  private final MeterRegistry meterRegistry;

  public EverythingDataSelector(
      Config common,
      WebClient client,
      PatientIdResolver patientIdResolver,
      MeterRegistry meterRegistry) {
    this.common = common;
    this.client = client;
    this.pidResolver = patientIdResolver;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Flux<Bundle> select(ConsentedPatient patient) {
    return pidResolver
        .resolve(patient.id())
        .flatMapMany(fhirId -> fetchEverything(patient, fhirId));
  }

  private Flux<Bundle> fetchEverything(ConsentedPatient patient, IIdType fhirId) {

    return fetchBundle(
            "/Patient/{id}/$everything",
            common.ignoreConsent() ? withoutConsent(fhirId) : withConsent(patient, fhirId))
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchEverything"))
        .doOnError(e -> log.error("Unable to fetch patient data from HDS: {}", e.getMessage()))
        .flux();
  }

  private Mono<Bundle> fetchBundle(String uri, Function<UriBuilder, URI> builder) {
    return client
        .get()
        .uri(uri, builder)
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .bodyToMono(Bundle.class);
  }

  private Function<UriBuilder, URI> withoutConsent(IIdType fhirId) {
    return (uriBuilder) -> uriBuilder.build(fhirId.getIdPart());
  }

  private Function<UriBuilder, URI> withConsent(ConsentedPatient patient, IIdType fhirId) {
    var period = patient.maxConsentedPeriod();
    if (period.isEmpty()) {
      throw new IllegalArgumentException(
          "Patient has no consent configured, and ignoreConsent is false.");
    }
    return (uriBuilder) ->
        uriBuilder
            .queryParam("start", formatWithSystemTZ(period.get().start()))
            .queryParam("end", formatWithSystemTZ(period.get().end()))
            .build(Map.of("id", fhirId.getIdPart()));
  }

  private static String formatWithSystemTZ(ZonedDateTime t) {
    return t.format(ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()));
  }
}
