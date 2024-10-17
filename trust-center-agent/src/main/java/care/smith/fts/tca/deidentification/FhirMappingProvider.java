package care.smith.fts.tca.deidentification;

import static care.smith.fts.tca.deidentification.DateShiftUtil.generate;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.lang.Long.parseLong;
import static java.lang.String.valueOf;
import static java.time.Duration.ofMillis;
import static java.util.stream.Collectors.toMap;
import static reactor.function.TupleUtils.function;

import care.smith.fts.tca.deidentification.configuration.TransportMappingConfiguration;
import care.smith.fts.util.tca.ResearchMappingResponse;
import care.smith.fts.util.tca.TCADomains;
import care.smith.fts.util.tca.TransportMappingRequest;
import care.smith.fts.util.tca.TransportMappingResponse;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.micrometer.core.instrument.MeterRegistry;
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
    var rMap = redisClient.reactive().<String, String>getMapCache(transferId);
    return rMap.expire(configuration.ttl())
        .then(fetchPseudonymAndSalts(r.patientId(), r.tcaDomains(), r.maxDateShift()))
        .flatMap(saveResearchMapping(r.patientId(), r.maxDateShift(), transportMapping, rMap))
        .map(cdShift -> new TransportMappingResponse(transferId, transportMapping, cdShift));
  }

  private Mono<Tuple3<String, String, String>> fetchPseudonymAndSalts(
      String patientId, TCADomains domains, Duration maxDateShift) {
    var saltKey = "Salt_" + patientId;
    var dateShiftKey = "%s_%s".formatted(maxDateShift.toString(), patientId);
    return Mono.zip(
        gpasClient.fetchOrCreatePseudonyms(domains.pseudonym(), patientId),
        gpasClient.fetchOrCreatePseudonyms(domains.salt(), saltKey),
        gpasClient.fetchOrCreatePseudonyms(domains.dateShift(), dateShiftKey));
  }

  /** Saves the research mapping in redis for later use by the rda. */
  private Function<Tuple3<String, String, String>, Mono<Duration>> saveResearchMapping(
      String patientId,
      Duration maxDateShift,
      Map<String, String> transportMapping,
      RMapReactive<String, String> rMap) {
    return function(
        (patientIdPseudonym, salt, dateShiftSalt) -> {
          var dateShifts = generate(dateShiftSalt, maxDateShift);
          var resolveMap =
              ImmutableMap.<String, String>builder()
                  .putAll(generateResearchMapping(salt, transportMapping))
                  .putAll(patientIdPseudonyms(patientId, patientIdPseudonym, transportMapping))
                  .put("dateShiftMillis", valueOf(dateShifts.rdDateShift().toMillis()))
                  .build();
          return rMap.putAll(resolveMap).thenReturn(dateShifts.cdDateShift());
        });
  }

  /** generate ids for all entries in the transport mapping */
  private Map<String, String> generateResearchMapping(
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
  public Mono<ResearchMappingResponse> fetchResearchMapping(String transferId) {
    RedissonReactiveClient redis = redisClient.reactive();
    return Mono.just(transferId)
        .flatMap(name -> redis.<String, String>getMapCache(name).readAllMap())
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchResearchMapping"))
        .map(FhirMappingProvider::buildResolveResponse);
  }

  private static ResearchMappingResponse buildResolveResponse(Map<String, String> map) {
    var mutableMap = new HashMap<>(map);
    var dateShiftValue = getDateShiftMillis(mutableMap);
    return new ResearchMappingResponse(mutableMap, dateShiftValue);
  }

  private static Duration getDateShiftMillis(HashMap<String, String> mutableMap) {
    long dateShiftMillis;
    try {
      dateShiftMillis = parseLong(mutableMap.remove("dateShiftMillis"));
    } catch (NumberFormatException e) {
      log.error("Failed to parse dateShiftMillis", e);
      throw new NumberFormatException("Invalid dateShiftMillis value.");
    }
    return ofMillis(dateShiftMillis);
  }
}
