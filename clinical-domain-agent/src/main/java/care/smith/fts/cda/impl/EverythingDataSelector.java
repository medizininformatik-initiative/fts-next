package care.smith.fts.cda.impl;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.cda.services.PatientIdResolver;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;

@Slf4j
public class EverythingDataSelector implements DataSelector {
  private final Config common;
  private final WebClient client;
  private final PatientIdResolver pidResolver;

  public EverythingDataSelector(
      Config common, WebClient client, PatientIdResolver patientIdResolver) {
    this.common = common;
    this.client = client;
    this.pidResolver = patientIdResolver;
  }

  @Override
  public Flux<Bundle> select(ConsentedPatient patient) {
    return pidResolver
        .resolve(patient.id())
        .flatMapMany(fhirId -> fetchEverything(patient, fhirId));
  }

  private Flux<Bundle> fetchEverything(ConsentedPatient patient, IIdType fhirId) {
    return client
        .get()
        .uri(builder -> buildUri(builder, patient).build(fhirId.getIdPart()))
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .bodyToMono(Bundle.class)
        .doOnError(e -> log.error("Unable to fetch patient data from HDS: {}", e.getMessage()))
        .retryWhen(defaultRetryStrategy())
        // TODO Paging using .expand()? see Flare
        .flux();
  }

  private UriBuilder buildUri(UriBuilder builder, ConsentedPatient patient) {
    builder = builder.pathSegment("Patient", "{id}", "$everything");
    if (!common.ignoreConsent()) {
      builder = addConsentParams(builder, patient);
    }
    return builder;
  }

  private UriBuilder addConsentParams(UriBuilder params, ConsentedPatient patient) {
    var period = patient.maxConsentedPeriod();
    if (period.isPresent()) {
      return params
          .queryParam("start", formatWithSystemTZ(period.get().start()))
          .queryParam("end", formatWithSystemTZ(period.get().end()));
    } else {
      throw new IllegalArgumentException(
          "Patient has no consent configured, and ignoreConsent is false.");
    }
  }

  private static String formatWithSystemTZ(ZonedDateTime t) {
    return t.format(ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()));
  }
}
