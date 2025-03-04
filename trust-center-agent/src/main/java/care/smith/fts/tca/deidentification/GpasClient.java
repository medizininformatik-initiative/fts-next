package care.smith.fts.tca.deidentification;

import static care.smith.fts.tca.TtpFhirGatewayUtil.handle4xxError;
import static care.smith.fts.tca.TtpFhirGatewayUtil.handleError;
import static care.smith.fts.tca.deidentification.configuration.GpasFhirDeIdentificationConfiguration.GPAS_OPERATIONS;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;

import care.smith.fts.util.RetryStrategies;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GpasClient {
  private final WebClient gpasClient;
  private final MeterRegistry meterRegistry;

  public GpasClient(
      @Qualifier("gpasFhirHttpClient") WebClient gpasClient, MeterRegistry meterRegistry) {
    this.gpasClient = gpasClient;
    this.meterRegistry = meterRegistry;
  }

  /**
   * @return Flux of (id, pid) tuples
   */
  public Mono<String> fetchOrCreatePseudonyms(String domain, String id) {
    var params =
        Map.of(
            "resourceType",
            "Parameters",
            "parameter",
            List.of(
                Map.of("name", "target", "valueString", domain),
                Map.of("name", "original", "valueString", id)));

    log.trace("fetchOrCreatePseudonyms for domain: {} and {}", domain, id);

    return gpasClient
        .post()
        .uri("/$pseudonymizeAllowCreate")
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .bodyValue(params)
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError,
            r1 -> handle4xxError("gPAS", gpasClient, GPAS_OPERATIONS, r1))
        .bodyToMono(GpasParameterResponse.class)
        .retryWhen(
            RetryStrategies.defaultRetryStrategy(meterRegistry, "fetchOrCreatePseudonymsOnGpas"))
        .onErrorResume(e -> (Mono<? extends GpasParameterResponse>) handleError("gPAS", e))
        .doOnError(e -> log.error("Unable to fetch pseudonym from gPAS: {}", e.getMessage()))
        .doOnNext(r -> log.trace("$pseudonymizeAllowCreate response: {}", r.parameter()))
        .map(GpasParameterResponse::getMappedID)
        .map(map -> map.get(id));
  }
}
