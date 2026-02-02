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

  record PseudonymData(String patientIdentifierPseudonym, String salt, String dateShiftSeed) {}

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
   * Stores secure mappings for CDA-provided transport IDs. CDA generates tIDs during
   * deidentification; TCA computes secure IDs (sIDs) from original IDs and stores tID→sID mappings.
   *
   * @param r the transport mapping request containing idMappings (originalID→tID) and dateMappings
   *     (tID→originalDate)
   * @return response containing the transferId for this session
   */
  @Override
  public Mono<TransportMappingResponse> generateTransportMapping(TransportMappingRequest r) {
    log.trace(
        "Store transport mapping for patientIdentifier={}, {} IDs, {} date tIDs",
        r.patientIdentifier(),
        r.idMappings().size(),
        r.dateMappings().size());

    var transferId = randomStringGenerator.generate();
    var sMap = redisClient.reactive().<String, String>getMapCache(transferId);

    return sMap.expire(configuration.getTtl())
        .then(fetchPseudonymAndSalts(r.patientIdentifier(), r.tcaDomains(), r.maxDateShift()))
        .flatMap(
            data -> {
              var dateShift =
                  generate(data.dateShiftSeed(), r.maxDateShift(), r.dateShiftPreserve());
              var tidToShiftedDate = computeTidToShiftedDate(r.dateMappings(), dateShift);

              return saveSecureMapping(r, data, tidToShiftedDate, sMap)
                  .thenReturn(new TransportMappingResponse(transferId));
            });
  }

  private Map<String, String> computeTidToShiftedDate(
      Map<String, String> dateMappings, Duration dateShift) {
    return dateMappings.entrySet().stream()
        .collect(toMap(Entry::getKey, e -> shiftDate(e.getValue(), dateShift)));
  }

  private Mono<PseudonymData> fetchPseudonymAndSalts(
      String patientIdentifier, TcaDomains domains, Duration maxDateShift) {
    var saltKey = "Salt_" + patientIdentifier;
    var dateShiftKey = "%s_%s".formatted(maxDateShift.toString(), patientIdentifier);
    return Mono.zip(
            gpasClient
                .fetchOrCreatePseudonyms(domains.pseudonym(), of(patientIdentifier))
                .map(m -> m.get(patientIdentifier)),
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
      Map<String, String> tidToShiftedDate,
      RMapReactive<String, String> rMap) {

    var resolveMapBuilder =
        ImmutableMap.<String, String>builder()
            .putAll(generateSecureMapping(data.salt(), r.idMappings()))
            .putAll(
                patientIdentifierPseudonyms(
                    r.patientIdentifier(),
                    r.patientIdentifierSystem(),
                    data.patientIdentifierPseudonym(),
                    r.idMappings()));

    // Store tID→shiftedDate mappings with prefix for RDA to resolve
    tidToShiftedDate.forEach(
        (tId, shiftedDate) -> resolveMapBuilder.put(DATE_SHIFT_PREFIX + tId, shiftedDate));

    return rMap.putAll(resolveMapBuilder.buildKeepingLast()).then();
  }

  /**
   * Generates secure ID mappings from transport IDs. For each originalID→tID mapping, computes
   * sID=hash(salt+originalID) and returns tID→sID mapping.
   *
   * @param transportSalt the salt for hashing
   * @param idMappings map of originalID→tID
   * @return map of tID→sID
   */
  static Map<String, String> generateSecureMapping(
      String transportSalt, Map<String, String> idMappings) {
    return idMappings.entrySet().stream()
        .collect(toMap(Entry::getValue, entry -> transportHash(transportSalt, entry.getKey())));
  }

  private static String transportHash(String transportSalt, String id) {
    return hashFn.hashString(transportSalt + id, StandardCharsets.UTF_8).toString();
  }

  /**
   * Extracts the patient identifier mapping and maps it to the gPAS pseudonym. This ensures the
   * patient's identifier in RDA is the de-identified identifier from gPAS for re-identification.
   *
   * @param patientIdentifier the patient identifier
   * @param patientIdentifierSystem the patient identifier system
   * @param patientIdentifierPseudonym the pseudonym from gPAS
   * @param idMappings map of originalID→tID
   * @return map of tID→patientIdentifierPseudonym for the patient identifier entry
   */
  static Map<String, String> patientIdentifierPseudonyms(
      String patientIdentifier,
      String patientIdentifierSystem,
      String patientIdentifierPseudonym,
      Map<String, String> idMappings) {
    var x = NamespacingReplacementProvider.withNamespacing(patientIdentifier);
    var name = x.getKeyForSystemAndValue(patientIdentifierSystem, patientIdentifier);

    return idMappings.entrySet().stream()
        .filter(entry -> entry.getKey().equals(name))
        .collect(toMap(Entry::getValue, id -> patientIdentifierPseudonym));
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
