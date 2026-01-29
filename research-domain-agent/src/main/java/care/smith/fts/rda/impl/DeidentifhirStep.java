package care.smith.fts.rda.impl;

import static care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil.generateRegistry;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil;
import care.smith.fts.util.tca.SecureMappingResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
class DeidentifhirStep implements Deidentificator {
  private final WebClient tcaClient;
  private final com.typesafe.config.Config deidentifhirConfig;
  private final MeterRegistry meterRegistry;

  public DeidentifhirStep(
      com.typesafe.config.Config config, WebClient tcaClient, MeterRegistry meterRegistry) {
    this.tcaClient = tcaClient;
    this.deidentifhirConfig = config;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<Bundle> deidentify(TransportBundle bundle) {
    return fetchSecureMapping(bundle.transferId())
        .map(
            response -> {
              // Apply ID replacement via deidentifhir
              var registry = generateRegistry(response.tidPidMap());
              var deidentified =
                  DeidentifhirUtil.deidentify(
                      deidentifhirConfig, registry, bundle.bundle(), meterRegistry);

              // Restore shifted dates from TCA using tID extensions
              DeidentifhirUtil.restoreShiftedDates(deidentified, response.dateShiftMap());

              return deidentified;
            })
        .doOnNext(b -> log.trace("Total bundle entries: {}", b.getEntry().size()));
  }

  private Mono<SecureMappingResponse> fetchSecureMapping(String transferId) {
    return tcaClient
        .post()
        .uri("/api/v2/rd/secure-mapping")
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(transferId)
        .retrieve()
        .bodyToMono(SecureMappingResponse.class)
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchSecureMapping"))
        .doOnError(e -> log.error("Unable to resolve transport IDs: {}", e.getMessage()));
  }
}
