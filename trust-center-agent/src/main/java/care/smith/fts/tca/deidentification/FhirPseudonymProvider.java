package care.smith.fts.tca.deidentification;

import static care.smith.fts.tca.deidentification.DateShiftUtil.generate;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.util.stream.Collectors.toMap;
import static reactor.function.TupleUtils.function;

import care.smith.fts.tca.deidentification.configuration.PseudonymizationConfiguration;
import care.smith.fts.util.tca.PseudonymizeResponse;
import care.smith.fts.util.tca.TCADomains;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

@Slf4j
@Component
public class FhirPseudonymProvider implements PseudonymProvider {
  private final GpasClient gpasClient;
  private final PseudonymizationConfiguration configuration;
  private final RedissonClient redisClient;

  private final MeterRegistry meterRegistry;
  private final RandomStringGenerator randomStringGenerator;
  private final HashFunction hashFn;

  public FhirPseudonymProvider(
      GpasClient gpasClient,
      RedissonClient redisClient,
      PseudonymizationConfiguration configuration,
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
   * @param ids the IDs to pseudonymize
   * @param tcaDomains the domains used in gPAS
   * @return Map<TID, PID>
   */
  @Override
  public Mono<PseudonymizeResponse> retrieveTransportIds(
      String patientId, Set<String> ids, TCADomains tcaDomains, Duration maxDateShift) {
    log.trace("retrieveTransportIds patientId={}, ids={}", patientId, ids);
    var tIDMapName = randomStringGenerator.generate();
    var originalToTransportIDMapping =
        ids.stream().collect(toMap(id -> id, id -> randomStringGenerator.generate()));
    var rMap = redisClient.reactive().getMapCache(tIDMapName);
    return rMap.expire(Duration.ofSeconds(configuration.getTransportIdTTLinSeconds()))
        .then(fetchPseudonymAndSalts(patientId, tcaDomains, maxDateShift))
        .flatMap(saveTransportIDs(patientId, rMap, originalToTransportIDMapping))
        .map(salt -> generate(salt, maxDateShift))
        .map(shift -> new PseudonymizeResponse(tIDMapName, originalToTransportIDMapping, shift));
  }

  private Function<Tuple3<String, String, String>, Mono<String>> saveTransportIDs(
      String patientId,
      RMapCacheReactive<Object, Object> rMap,
      Map<String, String> originalToTransportIDMapping) {
    return function(
        (patientIdPseudnym, salt, dateShiftSalt) -> {
          var transportToSecureIDMapping = generateTransportIDs(salt, originalToTransportIDMapping);
          replacePatientIdMapping(
              patientId,
              patientIdPseudnym,
              transportToSecureIDMapping,
              originalToTransportIDMapping);
          return rMap.putAll(transportToSecureIDMapping).thenReturn(dateShiftSalt);
        });
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

  private Map<String, String> generateTransportIDs(
      String transportSalt, Map<String, String> originalToTransportIDMapping) {
    return originalToTransportIDMapping.entrySet().stream()
        .collect(toMap(Entry::getValue, entry -> transportHash(transportSalt, entry.getKey())));
  }

  private String transportHash(String transportSalt, String id) {
    return hashFn.hashString(transportSalt + id, StandardCharsets.UTF_8).toString();
  }

  /**
   * With this function we make sure that the patient's ID in the RDA is the de-identified ID stored
   * in gPAS. This ensures that we can re-identify patients.
   */
  private static void replacePatientIdMapping(
      String patientId,
      String patientIdPseudonym,
      Map<String, String> transportToSecureIDMapping,
      Map<String, String> originalToTransportIDMapping) {
    if (originalToTransportIDMapping.keySet().stream()
        .anyMatch(id -> id.endsWith("Patient." + patientId))) {
      transportToSecureIDMapping.put(
          originalToTransportIDMapping.get("Patient." + patientId), patientIdPseudonym);
    }
  }

  @Override
  public Mono<Map<String, String>> fetchPseudonymizedIds(String tIDMapName) {
    RedissonReactiveClient redis = redisClient.reactive();
    return Mono.just(tIDMapName)
        .flatMap(name -> redis.getMapCache(name).readAllMap())
        .map(
            m ->
                m.entrySet().stream()
                    .collect(toMap(e -> (String) e.getKey(), e -> (String) e.getValue())))
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchPseudonymizedIds"));
  }
}
