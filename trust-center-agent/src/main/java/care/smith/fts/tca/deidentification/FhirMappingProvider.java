package care.smith.fts.tca.deidentification;

import static care.smith.fts.tca.deidentification.DateShiftUtil.generate;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.lang.String.valueOf;
import static java.util.Set.of;
import static java.util.stream.Collectors.toMap;

import care.smith.fts.tca.deidentification.CompartmentIdSplitter.CompartmentIds;
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
import java.util.Set;
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

  private record FetchedData(
      PseudonymData pseudonymData, Map<String, String> nonCompartmentPseudonyms) {}

  private final GpasClient gpasClient;
  private final TransportMappingConfiguration configuration;
  private final RedissonClient redisClient;
  private final MeterRegistry meterRegistry;
  private final RandomStringGenerator randomStringGenerator;
  private final CompartmentIdSplitter compartmentIdSplitter;

  public FhirMappingProvider(
      GpasClient gpasClient,
      RedissonClient redisClient,
      TransportMappingConfiguration configuration,
      MeterRegistry meterRegistry,
      RandomStringGenerator randomStringGenerator,
      CompartmentIdSplitter compartmentIdSplitter) {
    this.gpasClient = gpasClient;
    this.configuration = configuration;
    this.redisClient = redisClient;
    this.meterRegistry = meterRegistry;
    this.randomStringGenerator = randomStringGenerator;
    this.compartmentIdSplitter = compartmentIdSplitter;
  }

  /**
   * For all provided IDs fetch the id:pid pairs from gPAS. Then create TransportIDs (id:tid pairs).
   * Store tid:pid in the key-value-store.
   *
   * <p>IDs are split into two categories:
   *
   * <ul>
   *   <li>Patient-compartment IDs: pseudonymized using patient-derived salt (SHA256 hash)
   *   <li>Non-compartment IDs: pseudonymized directly via gPAS
   * </ul>
   */
  @Override
  public Mono<TransportMappingResponse> generateTransportMapping(TransportMappingRequest r) {
    log.trace("retrieveTransportIds patientId={}, resourceIds={}", r.patientId(), r.resourceIds());
    var transferId = randomStringGenerator.generate();

    var compartmentIds = compartmentIdSplitter.split(r.resourceIds(), r.patientId());
    log.trace(
        "Split IDs: {} in patient compartment, {} outside compartment",
        compartmentIds.inCompartment().size(),
        compartmentIds.outsideCompartment().size());

    var transportMapping =
        r.resourceIds().stream().collect(toMap(id -> id, id -> randomStringGenerator.generate()));

    var sMap = redisClient.reactive().<String, String>getMapCache(transferId);
    return sMap.expire(configuration.getTtl())
        .then(fetchPseudonymAndSalts(r.patientId(), r.tcaDomains(), r.maxDateShift()))
        .flatMap(
            pseudonymData ->
                fetchNonCompartmentPseudonyms(
                        compartmentIds.outsideCompartment(), r.tcaDomains().pseudonym())
                    .map(
                        nonCompartmentPseudonyms ->
                            new FetchedData(pseudonymData, nonCompartmentPseudonyms)))
        .flatMap(
            data ->
                saveSecureMappingWithCompartment(
                    r,
                    transportMapping,
                    compartmentIds,
                    data.nonCompartmentPseudonyms(),
                    sMap,
                    data.pseudonymData()))
        .map(cdShift -> new TransportMappingResponse(transferId, transportMapping, cdShift));
  }

  /** Fetches pseudonyms from gPAS for non-compartment resource IDs. */
  private Mono<Map<String, String>> fetchNonCompartmentPseudonyms(
      Set<String> nonCompartmentIds, String domain) {
    if (nonCompartmentIds.isEmpty()) {
      return Mono.just(Map.of());
    }
    return gpasClient.fetchOrCreatePseudonyms(domain, nonCompartmentIds);
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

  /**
   * Saves the research mapping in redis for later use by the rda, with compartment awareness.
   *
   * <p>Patient-compartment IDs are hashed with salt, non-compartment IDs use gPAS pseudonyms.
   */
  private Mono<Duration> saveSecureMappingWithCompartment(
      TransportMappingRequest r,
      Map<String, String> transportMapping,
      CompartmentIds compartmentIds,
      Map<String, String> nonCompartmentPseudonyms,
      RMapReactive<String, String> rMap,
      PseudonymData data) {
    var dateShifts = generate(data.dateShiftSeed(), r.maxDateShift(), r.dateShiftPreserve());

    var resolveMap =
        buildResolveMap(
            r,
            transportMapping,
            compartmentIds,
            nonCompartmentPseudonyms,
            data.salt(),
            data.patientIdPseudonym(),
            dateShifts);

    return rMap.putAll(resolveMap).thenReturn(dateShifts.cdDateShift());
  }

  private ImmutableMap<String, String> buildResolveMap(
      TransportMappingRequest r,
      Map<String, String> transportMapping,
      CompartmentIds compartmentIds,
      Map<String, String> nonCompartmentPseudonyms,
      String salt,
      String patientIdPseudonym,
      DateShiftUtil.DateShifts dateShifts) {
    var compartmentTransportMapping =
        filterTransportMapping(transportMapping, compartmentIds.inCompartment());
    var nonCompartmentTransportMapping =
        filterTransportMapping(transportMapping, compartmentIds.outsideCompartment());

    return ImmutableMap.<String, String>builder()
        .putAll(generateSecureMapping(salt, compartmentTransportMapping))
        .putAll(
            generateNonCompartmentMapping(nonCompartmentTransportMapping, nonCompartmentPseudonyms))
        .putAll(
            patientIdPseudonyms(
                r.patientId(), r.patientIdentifierSystem(), patientIdPseudonym, transportMapping))
        .put("dateShiftMillis", valueOf(dateShifts.rdDateShift().toMillis()))
        .buildKeepingLast();
  }

  private static Map<String, String> filterTransportMapping(
      Map<String, String> transportMapping, Set<String> ids) {
    return transportMapping.entrySet().stream()
        .filter(e -> ids.contains(e.getKey()))
        .collect(toMap(Entry::getKey, Entry::getValue));
  }

  static Map<String, String> generateNonCompartmentMapping(
      Map<String, String> transportMapping, Map<String, String> gpasPseudonyms) {
    return transportMapping.entrySet().stream()
        .filter(e -> gpasPseudonyms.containsKey(e.getKey()))
        .collect(toMap(Entry::getValue, e -> gpasPseudonyms.get(e.getKey())));
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
