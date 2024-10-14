package care.smith.fts.rda.impl;

import static care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil.generateRegistry;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil;
import care.smith.fts.util.tca.ResearchMappingResponse;
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
    return fetchResearchMapping(bundle.transferId())
        .map(
            p ->
                DeidentifhirUtil.deidentify(
                    deidentifhirConfig,
                    generateRegistry(p.tidPidMap(), p.dateShiftBy()),
                    bundle.bundle(),
                    meterRegistry))
        .doOnNext(b -> log.trace("Total bundle entries: {}", b.getEntry().size()));
  }

  private Mono<ResearchMappingResponse> fetchResearchMapping(String transferId) {
    return httpClient
        .post()
        .uri("/api/v2/rd/research-mapping")
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(transferId)
        .retrieve()
        .bodyToMono(ResearchMappingResponse.class)
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchResearchMapping"))
        .doOnError(e -> log.error("Unable to resolve transport IDs: {}", e.getMessage()));
  }
}
