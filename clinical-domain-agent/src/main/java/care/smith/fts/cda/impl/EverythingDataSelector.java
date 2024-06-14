package care.smith.fts.cda.impl;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.cda.services.PatientIdResolver;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;

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
    return client
        .get()
        .uri(
            builder -> {
              builder = builder.pathSegment("Patient", "{id}", "$everything");
              if (!common.ignoreConsent()) {
                builder = addConsentParams(builder, patient);
              }
              return builder.build(pidResolver.resolve(patient.id()));
            })
        .retrieve()
        .bodyToFlux(Bundle.class);
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
