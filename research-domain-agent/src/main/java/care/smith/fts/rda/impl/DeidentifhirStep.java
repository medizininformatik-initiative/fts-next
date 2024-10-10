package care.smith.fts.rda.impl;

import static care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil.generateRegistry;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil;
import care.smith.fts.util.tca.ResolveResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
class DeidentifhirStep implements Deidentificator {
  private final WebClient httpClient;
  private final com.typesafe.config.Config deidentifhirConfig;
  private final MeterRegistry meterRegistry;

  public DeidentifhirStep(
      com.typesafe.config.Config config,
      WebClient httpClient,
      MeterRegistry meterRegistry) {
    this.httpClient = httpClient;
    this.deidentifhirConfig = config;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<Bundle> deidentify(TransportBundle bundle) {
    return fetchPseudonymsForTransportIds(bundle.tIDMapName())
        .map(
            p ->
                DeidentifhirUtil.deidentify(
                    deidentifhirConfig,
                    generateRegistry(p.tidPidMap(), p.dateShiftBy()),
                    bundle.bundle(),
                    meterRegistry))
        .doOnNext(b -> log.trace("Total bundle entries: {}", b.getEntry().size()));
  }

  private Mono<ResolveResponse> fetchPseudonymsForTransportIds(String transportIDMapName) {
    return httpClient
        .post()
        .uri("/api/v2/rd/resolve-pseudonyms")
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(transportIDMapName)
        .retrieve()
        .bodyToMono(ResolveResponse.class)
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchPseudonymsForTransportIds"))
        .doOnError(e -> log.error("Unable to resolve transport IDs: {}", e.getMessage()));
  }
}
