package care.smith.fts.cda.impl;

import static care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils.generateRegistry;
import static java.util.Set.copyOf;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.DeidentificationProvider;
import care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils;
import care.smith.fts.cda.services.deidentifhir.IDATScraper;
import care.smith.fts.util.tca.*;
import care.smith.fts.util.tca.IDMap;
import java.time.Duration;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
class DeidentifhirStep implements DeidentificationProvider {
  private final WebClient httpClient;
  private final String domain;
  private final Duration dateShift;
  private final com.typesafe.config.Config deidentifhirConfig;
  private final com.typesafe.config.Config scraperConfig;

  public DeidentifhirStep(
      WebClient httpClient,
      String domain,
      Duration dateShift,
      com.typesafe.config.Config deidentifhirConfig,
      com.typesafe.config.Config scraperConfig) {
    this.httpClient = httpClient;
    this.domain = domain;
    this.dateShift = dateShift;
    this.deidentifhirConfig = deidentifhirConfig;
    this.scraperConfig = scraperConfig;
  }

  @Override
  public Flux<TransportBundle> deidentify(Flux<ConsentedPatientBundle> resourceFlux) {
    return resourceFlux.flatMap(this::deidentify);
  }

  private Mono<TransportBundle> deidentify(ConsentedPatientBundle bundle) {
    ConsentedPatient patient = bundle.consentedPatient();
    IDATScraper idatScraper = new IDATScraper(scraperConfig, patient);
    var ids = idatScraper.gatherIDs(bundle.bundle());
    return fetchTransportIdsAndDateShiftingValues(patient.id(), ids)
        .map(
            response -> {
              IDMap transportIDs = response.idMap();
              Duration dateShiftValue = response.dateShiftValue();

              log.info(patient.id());
              log.info(transportIDs.get(patient.id()));

              var registry = generateRegistry(patient.id(), transportIDs, dateShiftValue);
              var deidentified =
                  DeidentifhirUtils.deidentify(
                      deidentifhirConfig, registry, bundle.bundle(), patient.id());
              return new TransportBundle(deidentified, copyOf(transportIDs.values()));
            });
  }

  private Mono<PseudonymizeResponse> fetchTransportIdsAndDateShiftingValues(
      String patientId, Set<String> ids) {
    PseudonymizeRequest request = new PseudonymizeRequest(patientId, ids, domain, dateShift);

    return httpClient
        .post()
        .uri("/api/v2/cd/transport-ids-and-date-shifting-values")
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(request)
        .retrieve()
        .bodyToMono(PseudonymizeResponse.class);
  }
}
