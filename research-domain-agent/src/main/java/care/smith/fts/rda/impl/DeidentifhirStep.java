package care.smith.fts.rda.impl;

import static care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil.generateRegistry;
import static care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil.replaceIDs;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.Deidentificator;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
class DeidentifhirStep implements Deidentificator {
  private final WebClient httpClient;
  private final String domain;
  private final Duration dateShift; // TODO check if we have to do a second date shift in RDA
  private final com.typesafe.config.Config deidentifhirConfig;

  public DeidentifhirStep(
      com.typesafe.config.Config config, WebClient httpClient, String domain, Duration dateShift) {
    this.httpClient = httpClient;
    this.domain = domain;
    this.dateShift = dateShift;
    this.deidentifhirConfig = config;
  }

  @Override
  public Mono<Bundle> replaceIds(TransportBundle bundle) {
    return fetchPseudonymsForTransportIds(bundle.transportIds())
        .map(p -> replaceIDs(deidentifhirConfig, generateRegistry(p), bundle.bundle()))
        .doOnNext(b -> log.trace("total bundle entries: {}", b.getEntry().size()));
  }

  private Mono<Map<String, String>> fetchPseudonymsForTransportIds(Set<String> transportIds) {

    return httpClient
        .post()
        .uri("/api/v2/rd/resolve-pseudonyms")
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(transportIds)
        .retrieve()
        .bodyToMono(Object.class)
        .map(o -> (Map<String, String>) o)
        .retryWhen(defaultRetryStrategy());
  }
}
