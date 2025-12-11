package care.smith.fts.tca.deidentification;

import static care.smith.fts.tca.TtpFhirGatewayUtil.handle4xxError;
import static care.smith.fts.tca.TtpFhirGatewayUtil.handleError;
import static care.smith.fts.tca.deidentification.configuration.EnticiDeIdentificationConfiguration.ENTICI_OPERATIONS;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.util.List.of;

import care.smith.fts.tca.deidentification.configuration.EnticiDeIdentificationConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Client for communicating with Entici pseudonymization service.
 *
 * <p>This client calls the Entici FHIR API to create pseudonyms for identifiers. Since Entici does
 * not support native batch operations, batch requests are processed using client-side
 * parallelization.
 */
@Slf4j
public class EnticiClient {

  private final WebClient enticiClient;
  private final MeterRegistry meterRegistry;
  private final int concurrency;
  private final String resourceType;
  private final String project;

  public EnticiClient(
      WebClient enticiClient,
      MeterRegistry meterRegistry,
      EnticiDeIdentificationConfiguration config) {
    this.enticiClient = enticiClient;
    this.meterRegistry = meterRegistry;
    this.concurrency = config.getConcurrency();
    this.resourceType = config.getResourceType();
    this.project = config.getProject();
  }

  /**
   * Fetches or creates a pseudonym for a single identifier.
   *
   * @param domain the Entici domain (maps to identifier.system)
   * @param originalValue the original identifier value
   * @return Mono containing the pseudonym
   */
  public Mono<String> fetchOrCreatePseudonym(String domain, String originalValue) {
    log.trace("Fetching pseudonym from Entici: domain={}, originalValue={}", domain, originalValue);

    var requestBody = createRequest(domain, originalValue);

    return enticiClient
        .post()
        .uri("/$pseudonymize")
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .bodyValue(requestBody)
        .headers(h -> h.setAccept(of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError,
            r -> handle4xxError("Entici", enticiClient, ENTICI_OPERATIONS, r))
        .bodyToMono(EnticiParameterResponse.class)
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchOrCreatePseudonymOnEntici"))
        .onErrorResume(e -> handleError("Entici", e))
        .doOnError(e -> log.error("Unable to fetch pseudonym from Entici: {}", e.getMessage()))
        .doOnNext(r -> log.trace("$pseudonymize response: {}", r.parameter()))
        .map(EnticiParameterResponse::getPseudonymValue);
  }

  /**
   * Fetches or creates pseudonyms for multiple identifiers.
   *
   * <p>Since Entici does not support native batch operations, this method processes requests in
   * parallel with configurable concurrency.
   *
   * @param domain the Entici domain (maps to identifier.system)
   * @param originalValues the set of original identifier values
   * @return Mono of a map from original value to pseudonym
   */
  public Mono<Map<String, String>> fetchOrCreatePseudonyms(
      String domain, Set<String> originalValues) {
    if (originalValues.isEmpty()) {
      return Mono.just(Map.of());
    }

    log.trace("Fetching {} pseudonyms from Entici: domain={}", originalValues.size(), domain);

    return Flux.fromIterable(originalValues)
        .flatMap(
            original ->
                fetchOrCreatePseudonym(domain, original)
                    .map(pseudonym -> Map.entry(original, pseudonym)),
            concurrency)
        .collectMap(Map.Entry::getKey, Map.Entry::getValue);
  }

  private Map<String, Object> createRequest(String domain, String originalValue) {
    List<Map<String, Object>> params = new ArrayList<>();

    // Add identifier parameter
    params.add(
        Map.of(
            "name",
            "identifier",
            "valueIdentifier",
            Map.of(
                "system", domain,
                "value", originalValue)));

    // Add resourceType parameter
    params.add(Map.of("name", "resourceType", "valueString", resourceType));

    // Add project parameter if configured
    if (project != null && !project.isBlank()) {
      params.add(Map.of("name", "project", "valueString", project));
    }

    return Map.of("resourceType", "Parameters", "parameter", params);
  }
}
