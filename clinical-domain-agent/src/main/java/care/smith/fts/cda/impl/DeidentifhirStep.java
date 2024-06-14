package care.smith.fts.cda.impl;

import care.smith.fts.api.DeidentificationProvider;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.cda.services.deidentifhir.ConsentedPatientBundle;
import care.smith.fts.cda.services.deidentifhir.DeidentifhirService;
import care.smith.fts.cda.services.deidentifhir.IDATScraper;
import care.smith.fts.util.tca.*;
import care.smith.fts.util.tca.IDMap;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DeidentifhirStep
    implements DeidentificationProvider<ConsentedPatientBundle<Bundle>, TransportBundle<Bundle>> {
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
  public Flux<TransportBundle<Bundle>> deidentify(
      Flux<ConsentedPatientBundle<Bundle>> resourceFlux) {
    return resourceFlux.flatMap(
        bundle -> {
          IDATScraper idatScraper = new IDATScraper(scraperConfig, bundle.consentedPatient());
          var ids = idatScraper.gatherIDs(bundle.bundle());
          return fetchTransportIdsAndDateShiftingValues(ids)
              .map(
                  response -> {
                    IDMap transportIDs = response.getIdMap();
                    Duration dateShiftValue = response.getDateShiftValue();

                    DeidentifhirService deidentifhir =
                        new DeidentifhirService(
                            deidentifhirConfig,
                            bundle.consentedPatient(),
                            transportIDs,
                            dateShiftValue);
                    return new TransportBundle<>(
                        deidentifhir.deidentify(bundle.bundle()),
                        new HashSet<>(transportIDs.values()));
                  });
        });
  }

  private Mono<PseudonymizeResponse> fetchTransportIdsAndDateShiftingValues(Set<String> ids) {

    PseudonymizeRequest request = new PseudonymizeRequest();
    request.setIds(ids);
    request.setDomain(domain);
    request.setDateShift(dateShift);

    return httpClient
        .post()
        .uri("/cd/transport-ids-and-date-shifting-values")
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(request)
        .retrieve()
        .bodyToMono(PseudonymizeResponse.class);
  }
}
