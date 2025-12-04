package care.smith.fts.tca.deidentification;

import static care.smith.fts.tca.TtpFhirGatewayUtil.handle4xxError;
import static care.smith.fts.tca.TtpFhirGatewayUtil.handleError;
import static care.smith.fts.tca.deidentification.configuration.GpasDeIdentificationConfiguration.GPAS_OPERATIONS;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.util.List.of;

import care.smith.fts.tca.deidentification.configuration.GpasDeIdentificationConfiguration;
import com.google.common.collect.Lists;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GpasClient {
  private final WebClient gpasClient;
  private final MeterRegistry meterRegistry;
  private final int batchSize;
  private final int concurrency;

  public GpasClient(
      @Qualifier("gpasFhirHttpClient") WebClient gpasClient,
      MeterRegistry meterRegistry,
      GpasDeIdentificationConfiguration config) {
    this.gpasClient = gpasClient;
    this.meterRegistry = meterRegistry;
    this.batchSize = config.getBatchSize();
    this.concurrency = config.getConcurrency();
  }

  /**
   * Fetches or creates pseudonyms for multiple IDs, processing in batches to avoid overwhelming
   * gPAS.
   *
   * @param domain the gPAS domain
   * @param ids the set of IDs to pseudonymize
   * @return Mono of a map from original ID to pseudonym
   */
  public Mono<Map<String, String>> fetchOrCreatePseudonyms(String domain, Set<String> ids) {
    if (ids.isEmpty()) {
      return Mono.just(Map.of());
    }

    List<List<String>> batches = Lists.partition(new ArrayList<>(ids), batchSize);
    log.trace(
        "fetchOrCreatePseudonyms for domain: {} with {} IDs in {} batches",
        domain,
        ids.size(),
        batches.size());

    return Flux.fromIterable(batches)
        .flatMap(batch -> fetchBatch(domain, batch), concurrency)
        .reduce(
            new HashMap<String, String>(),
            (acc, batch) -> {
              acc.putAll(batch);
              return acc;
            })
        .map(Map::copyOf);
  }

  private Mono<Map<String, String>> fetchBatch(String domain, List<String> ids) {
    List<Map<String, String>> params = new ArrayList<>();
    params.add(param("target", domain));
    ids.forEach(id -> params.add(param("original", id)));

    var paramsResource = paramsResource(params);
    log.trace("fetchOrCreatePseudonyms batch for domain: {} with {} IDs", domain, ids.size());

    return gpasClient
        .post()
        .uri("/$pseudonymizeAllowCreate")
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .bodyValue(paramsResource)
        .headers(h -> h.setAccept(of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError,
            r -> handle4xxError("gPAS", gpasClient, GPAS_OPERATIONS, r))
        .bodyToMono(GpasParameterResponse.class)
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchOrCreatePseudonymsOnGpas"))
        .onErrorResume(e -> handleError("gPAS", e))
        .doOnError(e -> log.error("Unable to fetch pseudonyms from gPAS: {}", e.getMessage()))
        .doOnNext(r -> log.trace("$pseudonymizeAllowCreate batch response: {}", r.parameter()))
        .map(GpasParameterResponse::getMappedID);
  }

  private static Map<String, Object> paramsResource(List<Map<String, String>> params) {
    return Map.of("resourceType", "Parameters", "parameter", params);
  }

  private static Map<String, String> param(String target, String domain) {
    return Map.of("name", target, "valueString", domain);
  }
}
