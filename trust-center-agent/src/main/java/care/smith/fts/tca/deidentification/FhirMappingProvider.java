package care.smith.fts.tca.deidentification;

import static care.smith.fts.tca.deidentification.DateShiftUtil.generate;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.lang.String.valueOf;
import static java.util.stream.Collectors.toMap;
import static reactor.function.TupleUtils.function;

import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.tca.deidentification.configuration.TransportMappingConfiguration;
import care.smith.fts.util.tca.SecureMappingResponse;
import care.smith.fts.util.tca.TCADomains;
import care.smith.fts.util.tca.TransportMappingRequest;
import care.smith.fts.util.tca.TransportMappingResponse;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

@Slf4j
@Component
public class FhirMappingProvider implements MappingProvider {
  private final GpasClient gpasClient;
  private final TransportMappingConfiguration configuration;
  private final RedissonClient redisClient;

  private final MeterRegistry meterRegistry;
  private final RandomStringGenerator randomStringGenerator;
  private final HashFunction hashFn;

  public FhirMappingProvider(
      GpasClient gpasClient,
      RedissonClient redisClient,
      TransportMappingConfiguration configuration,
      MeterRegistry meterRegistry,
      RandomStringGenerator randomStringGenerator) {
    this.gpasClient = gpasClient;
    this.configuration = configuration;
    this.redisClient = redisClient;
    this.meterRegistry = meterRegistry;
    this.randomStringGenerator = randomStringGenerator;
    this.hashFn = Hashing.sha256();
  }

  /**
   * For all provided IDs fetch the id:pid pairs from gPAS. Then create TransportIDs (id:tid pairs).
   * Store tid:pid in the key-value-store.
   *
   * @param r the transport mapping request
   * @return Map<TID, PID>
   */
  @Override
  public Mono<TransportMappingResponse> generateTransportMapping(TransportMappingRequest r) {
    log.trace("retrieveTransportIds patientId={}, resourceIds={}", r.patientId(), r.resourceIds());
    var transferId = randomStringGenerator.generate();
    var transportMapping =
        r.resourceIds().stream().collect(toMap(id -> id, id -> randomStringGenerator.generate()));
    var sMap = redisClient.reactive().<String, String>getMapCache(transferId);
    return sMap.expire(configuration.getTtl())
        .then(fetchPseudonymAndSalts(r.patientId(), r.tcaDomains(), r.maxDateShift()))
        .flatMap(
            saveSecureMapping(
                r.patientId(), r.maxDateShift(), r.dateShiftPreserve(), transportMapping, sMap))
        .map(cdShift -> new TransportMappingResponse(transferId, transportMapping, cdShift));
  }

  private Mono<Tuple3<String, String, String>> fetchPseudonymAndSalts(
      String patientId, TCADomains domains, Duration maxDateShift) {
    var saltKey = "Salt_" + patientId;
    var dateShiftKey = "%s_%s".formatted(maxDateShift.toString(), patientId);
    return Mono.zip(
        gpasClient.fetchOrCreatePseudonym(domains.pseudonym(), patientId),
        gpasClient.fetchOrCreatePseudonym(domains.salt(), saltKey),
        gpasClient.fetchOrCreatePseudonym(domains.dateShift(), dateShiftKey));
  }

  /** Saves the research mapping in redis for later use by the rda. */
  private Function<Tuple3<String, String, String>, Mono<Duration>> saveSecureMapping(
      String patientId,
      Duration maxDateShift,
      @NotNull DateShiftPreserve preserve,
      Map<String, String> transportMapping,
      RMapReactive<String, String> rMap) {
    return function(
        (patientIdPseudonym, salt, dateShiftSeed) -> {
          var dateShifts = generate(dateShiftSeed, maxDateShift, preserve);
          var resolveMap =
              ImmutableMap.<String, String>builder()
                  .putAll(generateSecureMapping(salt, transportMapping))
                  .putAll(patientIdPseudonyms(patientId, patientIdPseudonym, transportMapping))
                  .put("dateShiftMillis", valueOf(dateShifts.rdDateShift().toMillis()))
                  .build();
          return rMap.putAll(resolveMap).thenReturn(dateShifts.cdDateShift());
        });
  }

  /** generate ids for all entries in the transport mapping */
  private Map<String, String> generateSecureMapping(
      String transportSalt, Map<String, String> transportMapping) {
    return transportMapping.entrySet().stream()
        .collect(toMap(Entry::getValue, entry -> transportHash(transportSalt, entry.getKey())));
  }

  /** hash a transport id using the salt */
  private String transportHash(String transportSalt, String id) {
    return hashFn.hashString(transportSalt + id, StandardCharsets.UTF_8).toString();
  }

  /**
   * With this function we make sure that the patient's ID in the RDA is the de-identified ID stored
   * in gPAS. This ensures that we can re-identify patients.
   */
  private static Map<String, String> patientIdPseudonyms(
      String patientId, String patientIdPseudonym, Map<String, String> transportMapping) {
    return transportMapping.keySet().stream()
        .filter(id -> id.endsWith("Patient." + patientId))
        .collect(toMap(id -> id, id -> patientIdPseudonym));
  }

  @Override
  public Mono<SecureMappingResponse> fetchSecureMapping(String transferId) {
    RedissonReactiveClient redis = redisClient.reactive();
    return Mono.just(transferId)
        .flatMap(name -> redis.<String, String>getMapCache(name).readAllMap())
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchSecureMapping"))
        .map(SecureMappingResponse::buildResolveResponse);
  }
}
