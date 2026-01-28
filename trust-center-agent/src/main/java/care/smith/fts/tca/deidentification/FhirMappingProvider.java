package care.smith.fts.tca.deidentification;

import static care.smith.fts.tca.deidentification.DateShiftUtil.generate;
import static care.smith.fts.tca.deidentification.DateShiftUtil.shiftDate;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_PREFIX;
import static java.util.Set.of;
import static java.util.stream.Collectors.toMap;

import care.smith.fts.tca.deidentification.configuration.TransportMappingConfiguration;
import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import care.smith.fts.util.tca.SecureMappingResponse;
import care.smith.fts.util.tca.TcaDomains;
import care.smith.fts.util.tca.TransportMappingRequest;
import care.smith.fts.util.tca.TransportMappingResponse;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class FhirMappingProvider implements MappingProvider {
  private static final HashFunction hashFn = Hashing.sha256();

  record PseudonymData(String patientIdPseudonym, String salt, String dateShiftSeed) {}

  private final GpasClient gpasClient;
  private final TransportMappingConfiguration configuration;
  private final RedissonClient redisClient;
  private final MeterRegistry meterRegistry;
  private final RandomStringGenerator randomStringGenerator;

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
  }

  /**
   * For all provided IDs and date transport mappings, generate transport mappings and compute
   * shifted dates. Stores tID→shiftedDate mappings in Redis for RDA retrieval.
   *
   * @param r the transport mapping request containing tID→originalDate mappings
   * @return response containing transport mappings (dateShiftMapping is empty as dates are handled
   *     via tIDs)
   */
  @Override
  public Mono<TransportMappingResponse> generateTransportMapping(TransportMappingRequest r) {
    log.trace(
        "Generate transport mapping for patientId={}, {} IDs, {} date tIDs",
        r.patientId(),
        r.resourceIds().size(),
        r.dateTransportMappings().size());

    var transferId = randomStringGenerator.generate();
    var transportMapping =
        r.resourceIds().stream().collect(toMap(id -> id, id -> randomStringGenerator.generate()));
    var sMap = redisClient.reactive().<String, String>getMapCache(transferId);

    return sMap.expire(configuration.getTtl())
        .then(fetchPseudonymAndSalts(r.patientId(), r.tcaDomains(), r.maxDateShift()))
        .flatMap(
            data -> {
              var dateShift =
                  generate(data.dateShiftSeed(), r.maxDateShift(), r.dateShiftPreserve());
              // Compute tID→shiftedDate from tID→originalDate
              var tidToShiftedDate = computeTidToShiftedDate(r.dateTransportMappings(), dateShift);

              return saveSecureMapping(r, data, transportMapping, tidToShiftedDate, sMap)
                  // Return empty dateShiftMapping - RDA will resolve tIDs from extensions
                  .thenReturn(new TransportMappingResponse(transferId, transportMapping, Map.of()));
            });
  }

  private Map<String, String> computeTidToShiftedDate(
      Map<String, String> dateTransportMappings, Duration dateShift) {
    return dateTransportMappings.entrySet().stream()
        .collect(toMap(Entry::getKey, e -> shiftDate(e.getValue(), dateShift)));
  }

  private Mono<PseudonymData> fetchPseudonymAndSalts(
      String patientId, TcaDomains domains, Duration maxDateShift) {
    var saltKey = "Salt_" + patientId;
    var dateShiftKey = "%s_%s".formatted(maxDateShift.toString(), patientId);
    return Mono.zip(
            gpasClient
                .fetchOrCreatePseudonyms(domains.pseudonym(), of(patientId))
                .map(m -> m.get(patientId)),
            gpasClient
                .fetchOrCreatePseudonyms(domains.salt(), of(saltKey))
                .map(m -> m.get(saltKey)),
            gpasClient
                .fetchOrCreatePseudonyms(domains.dateShift(), of(dateShiftKey))
                .map(m -> m.get(dateShiftKey)))
        .map(t -> new PseudonymData(t.getT1(), t.getT2(), t.getT3()));
  }

  private Mono<Void> saveSecureMapping(
      TransportMappingRequest r,
      PseudonymData data,
      Map<String, String> transportMapping,
      Map<String, String> tidToShiftedDate,
      RMapReactive<String, String> rMap) {

    var resolveMapBuilder =
        ImmutableMap.<String, String>builder()
            .putAll(generateSecureMapping(data.salt(), transportMapping))
            .putAll(
                patientIdPseudonyms(
                    r.patientId(),
                    r.patientIdentifierSystem(),
                    data.patientIdPseudonym(),
                    transportMapping));

    // Store tID→shiftedDate mappings with prefix for RDA to resolve
    tidToShiftedDate.forEach(
        (tId, shiftedDate) -> resolveMapBuilder.put(DATE_SHIFT_PREFIX + tId, shiftedDate));

    return rMap.putAll(resolveMapBuilder.buildKeepingLast()).then();
  }

  static Map<String, String> generateSecureMapping(
      String transportSalt, Map<String, String> transportMapping) {
    return transportMapping.entrySet().stream()
        .collect(toMap(Entry::getValue, entry -> transportHash(transportSalt, entry.getKey())));
  }

  private static String transportHash(String transportSalt, String id) {
    return hashFn.hashString(transportSalt + id, StandardCharsets.UTF_8).toString();
  }

  static Map<String, String> patientIdPseudonyms(
      String patientId,
      String patientIdentifierSystem,
      String patientIdPseudonym,
      Map<String, String> transportMapping) {
    var x = NamespacingReplacementProvider.withNamespacing(patientId);
    var name = x.getKeyForSystemAndValue(patientIdentifierSystem, patientId);

    return transportMapping.entrySet().stream()
        .filter(entry -> entry.getKey().equals(name))
        .collect(toMap(Entry::getValue, id -> patientIdPseudonym));
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
