package care.smith.fts.tca.deidentification;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;

import care.smith.fts.tca.deidentification.configuration.PseudonymizationConfiguration;
import care.smith.fts.util.error.UnknownDomainException;
import java.time.Duration;
import java.util.*;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
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
   * @return the TransportIDs
   */
  @Override
  public Mono<Map<String, String>> retrieveTransportIds(Set<String> ids, String domain) {
    RedissonReactiveClient redis = redisClient.reactive();

    return fetchOrCreatePseudonyms(domain, ids)
        .flatMap(
            tuple ->
                getUniqueTransportId().map(tid -> Tuples.of(tuple.getT1(), tid, tuple.getT2())))
        .flatMap(
            tuple3 ->
                redis
                    .getBucket("tid:" + tuple3.getT2())
                    .setIfAbsent(
                        tuple3.getT3(),
                        Duration.ofSeconds(configuration.getTransportIdTTLinSeconds()))
                    .map(ret -> tuple3))
        .collectMap(Tuple3::getT1, Tuple3::getT2);
  }

  /** Generate a random transport ID and make sure it does not yet exist in the key-value-store. */
  private Mono<String> getUniqueTransportId() {
    var tid =
        randomGenerator
            .ints(9, 0, ALLOWED_PSEUDONYM_CHARS.length())
            .mapToObj(ALLOWED_PSEUDONYM_CHARS::charAt)
            .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
            .toString();

    RedissonReactiveClient redis = redisClient.reactive();
    return redis
        .getBucket("tid:" + tid)
        .get()
        .switchIfEmpty(Mono.just("OK"))
        .doOnError(e -> log.error("error: {}", e.getMessage()))
        .flatMap(ret -> ret.equals("OK") ? Mono.just(tid) : getUniqueTransportId());
  }

  private Flux<Tuple2<String, String>> fetchOrCreatePseudonyms(String domain, Set<String> ids) {
    var idParams =
        Stream.concat(
            Stream.of(Map.of("name", "target", "valueString", domain)),
            ids.stream().map(id -> Map.of("name", "original", "valueString", id)));
    var params = Map.of("resourceType", "Parameters", "parameter", idParams.toList());

    log.trace(
        "fetchOrCreatePseudonyms for domain: %s and ids: %s".formatted(domain, ids.toString()));

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
        .retryWhen(defaultRetryStrategy())
        .doOnNext(r -> log.trace("$pseudonymize response: {}", r))
        .map(GpasParameterResponse::getMappedID)
        .flatMapMany(
            map -> Flux.fromIterable(map.entrySet()).map(e -> Tuples.of(e.getKey(), e.getValue())));
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
  public Mono<Map<String, String>> fetchPseudonymizedIds(Set<String> ids) {
    if (!ids.isEmpty()) {
      RedissonReactiveClient redis = redisClient.reactive();
      return Flux.fromIterable(ids)
          .flatMap(
              id -> redis.<String>getBucket("tid:" + id).get().map(value -> Tuples.of(id, value)))
          .collectMap(Tuple2::getT1, Tuple2::getT2);

    } else {
      return Mono.empty();
    }
  }
}
