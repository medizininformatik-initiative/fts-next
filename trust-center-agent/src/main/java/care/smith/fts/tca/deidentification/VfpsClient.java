package care.smith.fts.tca.deidentification;

import static care.smith.fts.tca.TtpFhirGatewayUtil.handle4xxError;
import static care.smith.fts.tca.TtpFhirGatewayUtil.handleError;
import static care.smith.fts.tca.deidentification.configuration.VfpsDeIdentificationConfiguration.VFPS_OPERATIONS;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.util.List.of;

import care.smith.fts.tca.deidentification.configuration.VfpsDeIdentificationConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Client for communicating with Vfps (Very Fast Pseudonym Service).
 *
 * <p>This client calls the Vfps REST API to create pseudonyms for identifiers. Since Vfps does not
 * support native batch operations, batch requests are processed using client-side parallelization.
 */
@Slf4j
public class VfpsClient {

  private final WebClient vfpsClient;
  private final MeterRegistry meterRegistry;
  private final int concurrency;

  public VfpsClient(
      WebClient vfpsClient, MeterRegistry meterRegistry, VfpsDeIdentificationConfiguration config) {
    this.vfpsClient = vfpsClient;
    this.meterRegistry = meterRegistry;
    this.concurrency = config.getConcurrency();
  }

  /**
   * Fetches or creates a pseudonym for a single identifier.
   *
   * @param namespace the Vfps namespace (domain)
   * @param originalValue the original identifier value
   * @return Mono containing the pseudonym
   */
  public Mono<String> fetchOrCreatePseudonym(String namespace, String originalValue) {
    log.trace(
        "Fetching pseudonym from Vfps: namespace={}, originalValue={}", namespace, originalValue);

    var requestBody = createRequest(namespace, originalValue);

    return vfpsClient
        .post()
        .uri("/$create-pseudonym")
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .bodyValue(requestBody)
        .headers(h -> h.setAccept(of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError,
            r -> handle4xxError("Vfps", vfpsClient, VFPS_OPERATIONS, r))
        .bodyToMono(VfpsParameterResponse.class)
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchOrCreatePseudonymOnVfps"))
        .onErrorResume(e -> handleError("Vfps", e))
        .doOnError(e -> log.error("Unable to fetch pseudonym from Vfps: {}", e.getMessage()))
        .doOnNext(r -> log.trace("$create-pseudonym response: {}", r.parameter()))
        .map(VfpsParameterResponse::getPseudonymValue);
  }

  /**
   * Fetches or creates pseudonyms for multiple identifiers.
   *
   * <p>Since Vfps does not support native batch operations, this method processes requests in
   * parallel with configurable concurrency.
   *
   * @param namespace the Vfps namespace (domain)
   * @param originalValues the set of original identifier values
   * @return Mono of a map from original value to pseudonym
   */
  public Mono<Map<String, String>> fetchOrCreatePseudonyms(
      String namespace, Set<String> originalValues) {
    if (originalValues.isEmpty()) {
      return Mono.just(Map.of());
    }

    log.trace("Fetching {} pseudonyms from Vfps: namespace={}", originalValues.size(), namespace);

    return Flux.fromIterable(originalValues)
        .flatMap(
            original ->
                fetchOrCreatePseudonym(namespace, original)
                    .map(pseudonym -> Map.entry(original, pseudonym)),
            concurrency)
        .collectMap(Map.Entry::getKey, Map.Entry::getValue);
  }

  private static Map<String, Object> createRequest(String namespace, String originalValue) {
    return Map.of(
        "resourceType",
        "Parameters",
        "parameter",
        List.of(param("namespace", namespace), param("originalValue", originalValue)));
  }

  private static Map<String, String> param(String name, String value) {
    return Map.of("name", name, "valueString", value);
  }
}
