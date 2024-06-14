package care.smith.fts.rda.impl;

import care.smith.fts.api.DeidentificationProvider;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.services.deidentifhir.DeidentifhirService;
import care.smith.fts.util.tca.*;
import java.time.Duration;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DeidentifhirDeidentificationProvider
    implements DeidentificationProvider<TransportBundle<Bundle>, Bundle> {
  private final WebClient httpClient;
  private final String domain;
  private final Duration dateShift;
  private final com.typesafe.config.Config deidentifhirConfig;

  public DeidentifhirDeidentificationProvider(
      com.typesafe.config.Config config, WebClient httpClient, String domain, Duration dateShift) {
    this.httpClient = httpClient;
    this.domain = domain;
    this.dateShift = dateShift;
    this.deidentifhirConfig = config;
  }

  @Override
  public Flux<Bundle> deidentify(Flux<TransportBundle<Bundle>> bundleFlux) {
    return bundleFlux.flatMap(
        bundle ->
            fetchPseudonymsForTransportIds(bundle.transportIds())
                .map(
                    p -> {
                      DeidentifhirService deidentifhir =
                          new DeidentifhirService(deidentifhirConfig, p.getIdMap());

                      return deidentifhir.replaceIDs(bundle.bundle());
                    }));
  }

  private Mono<PseudonymizeResponse> fetchPseudonymsForTransportIds(Set<String> transportIds) {

    PseudonymizeRequest request = new PseudonymizeRequest();
    request.setIds(transportIds);
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
