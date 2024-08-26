package care.smith.fts.tca.deidentification;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.util.stream.Collectors.toMap;

import care.smith.fts.tca.deidentification.configuration.PseudonymizationConfiguration;
import care.smith.fts.util.error.UnknownDomainException;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
@Component
public class FhirPseudonymProvider implements PseudonymProvider {
  private static final String ALLOWED_PSEUDONYM_CHARS =
      "0123456789abcdefghijklmnopqrstuvwxyz-_ABCDEFGHIJKLMNOPQRSTUVWXYZ";

  private final WebClient httpClient;
  private final PseudonymizationConfiguration configuration;
  private final RedissonClient redisClient;
  private final RandomGenerator randomGenerator;

  public FhirPseudonymProvider(
      @Qualifier("gpasFhirHttpClient") WebClient httpClient,
      RedissonClient redisClient,
      PseudonymizationConfiguration configuration,
      RandomGenerator randomGenerator) {
    this.httpClient = httpClient;
    this.configuration = configuration;
    this.redisClient = redisClient;
    this.randomGenerator = randomGenerator;
  }

  /**
   * For all provided IDs fetch the id:pid pairs from gPAS. Then create TransportIDs (id:tid pairs).
   * Store tid:pid in the key-value-store.
   *
   * @param ids the IDs to pseudonymize
   * @param domain the domain used in gPAS
   * @return Map<TID, PID>
   */
  @Override
  public Mono<Tuple2<String, Map<String, String>>> retrieveTransportIds(
      String patientId, Set<String> ids, String domain) {

    if (!ids.contains(patientId)) {
      throw new IllegalArgumentException("patientId must be part of ids");
    }
    var saltKey = "Salt_" + patientId;
    var tIDMapName = generateTID();
    var originalToTransportIDMapping = ids.stream().collect(toMap(id -> id, id -> generateTID()));
    var rMap = redisClient.reactive().getMapCache(tIDMapName);
    return rMap.expire(Duration.ofSeconds(configuration.getTransportIdTTLinSeconds()))
        .map(ignore -> rMap)
        .flatMap(ignore -> fetchOrCreatePseudonyms(domain, Set.of(patientId, saltKey)))
        .flatMap(
            originalToSecureIDMapping -> {
              var sha256 = Hashing.sha256();
              var salt = originalToSecureIDMapping.get(saltKey);
              var transportToSecureIDMapping =
                  originalToTransportIDMapping.entrySet().stream()
                      .collect(
                          toMap(
                              Entry::getValue,
                              entry ->
                                  sha256
                                      .hashString(salt + entry.getKey(), StandardCharsets.UTF_8)
                                      .toString()));
              transportToSecureIDMapping.put(
                  originalToTransportIDMapping.get(patientId),
                  originalToSecureIDMapping.get(patientId));
              return rMap.putAll(transportToSecureIDMapping);
            })
        .then(Mono.fromCallable(() -> Tuples.of(tIDMapName, originalToTransportIDMapping)));
  }

  private String generateTID() {
    return randomGenerator
        .ints(9, 0, ALLOWED_PSEUDONYM_CHARS.length())
        .mapToObj(ALLOWED_PSEUDONYM_CHARS::charAt)
        .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
        .toString();
  }

  /**
   * @return Flux of (id, pid) tuples
   */
  private Mono<Map<String, String>> fetchOrCreatePseudonyms(String domain, Set<String> ids) {
    var idParams =
        Stream.concat(
            Stream.of(Map.of("name", "target", "valueString", domain)),
            ids.stream().map(id -> Map.of("name", "original", "valueString", id)));
    var params = Map.of("resourceType", "Parameters", "parameter", idParams.toList());

    log.trace("fetchOrCreatePseudonyms for domain: %s and %d ids".formatted(domain, ids.size()));

    return httpClient
        .post()
        .uri("/$pseudonymizeAllowCreate")
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .bodyValue(params)
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .onStatus(
            r -> r.equals(HttpStatus.BAD_REQUEST), FhirPseudonymProvider::handleGpasBadRequest)
        .bodyToMono(GpasParameterResponse.class)
        .doOnError(e -> log.error("Unable to fetch pseudonym from gPAS: {}", e.getMessage()))
        .retryWhen(defaultRetryStrategy())
        .doOnNext(r -> log.trace("$pseudonymize response: {} parameters", r.parameter().size()))
        .map(GpasParameterResponse::getMappedID);
  }

  private static Mono<Throwable> handleGpasBadRequest(ClientResponse r) {
    return r.bodyToMono(OperationOutcome.class)
        .flatMap(
            b -> {
              var diagnostics = b.getIssueFirstRep().getDiagnostics();
              log.error("Bad Request: {}", diagnostics);
              if (diagnostics != null && diagnostics.startsWith("Unknown domain")) {
                return Mono.error(new UnknownDomainException(diagnostics));
              } else {
                return Mono.error(new UnknownError());
              }
            });
  }

  @Override
  public Mono<Map<String, String>> fetchPseudonymizedIds(String tIDMapName) {
    RedissonReactiveClient redis = redisClient.reactive();
    return Mono.just(tIDMapName)
        .flatMap(name -> redis.getMapCache(name).readAllMap())
        .map(
            m ->
                m.entrySet().stream()
                    .collect(toMap(e -> (String) e.getKey(), e -> (String) e.getValue())));
  }
}
