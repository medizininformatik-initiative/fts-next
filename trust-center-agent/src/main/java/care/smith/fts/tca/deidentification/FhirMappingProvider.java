package care.smith.fts.tca.deidentification;

import static care.smith.fts.tca.deidentification.DateShiftUtil.generate;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.lang.String.valueOf;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

  // Pattern to extract first segment after prefix: {patientId}.{segment}...
  // For resource IDs: {patientId}.{ResourceType}:{id} → captures ResourceType
  // For identifiers: {patientId}.identifier.{system}:{value} → captures "identifier"
  private static final Pattern RESOURCE_ID_PATTERN = Pattern.compile("^[^.]+\\.([^.:]+)");

  record PseudonymData(String patientIdPseudonym, String salt, String dateShiftSeed) {}

  private record FetchedData(
      PseudonymData pseudonymData, Map<String, String> nonCompartmentPseudonyms) {}

  private record CompartmentIds(Set<String> inCompartment, Set<String> outsideCompartment) {}

  private final GpasClient gpasClient;
  private final TransportMappingConfiguration configuration;
  private final RedissonClient redisClient;
  private final MeterRegistry meterRegistry;
  private final RandomStringGenerator randomStringGenerator;
  private final PatientCompartment patientCompartment;

  public FhirMappingProvider(
      GpasClient gpasClient,
      RedissonClient redisClient,
      TransportMappingConfiguration configuration,
      MeterRegistry meterRegistry,
      RandomStringGenerator randomStringGenerator,
      PatientCompartment patientCompartment) {
    this.gpasClient = gpasClient;
    this.configuration = configuration;
    this.redisClient = redisClient;
    this.meterRegistry = meterRegistry;
    this.randomStringGenerator = randomStringGenerator;
    this.patientCompartment = patientCompartment;
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
   *
   * @param r the transport mapping request
   * @return Map<TID, PID>
   */
  @Override
  public Mono<TransportMappingResponse> generateTransportMapping(TransportMappingRequest r) {
    log.trace("retrieveTransportIds patientId={}, resourceIds={}", r.patientId(), r.resourceIds());
    var transferId = randomStringGenerator.generate();

    // Split IDs by compartment membership
    var compartmentIds = splitByCompartment(r.resourceIds());
    log.trace(
        "Split IDs: {} in patient compartment, {} outside compartment",
        compartmentIds.inCompartment().size(),
        compartmentIds.outsideCompartment().size());

    // Create transport mapping for all IDs
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

  /** Splits resource IDs into patient-compartment and non-compartment sets. */
  private CompartmentIds splitByCompartment(Set<String> resourceIds) {
    Map<Boolean, Set<String>> partitioned =
        resourceIds.stream()
            .collect(Collectors.partitioningBy(this::isInPatientCompartment, Collectors.toSet()));
    return new CompartmentIds(partitioned.get(true), partitioned.get(false));
  }

  /**
   * Checks if a resource ID belongs to the patient compartment.
   *
   * <p>IDs are in the patient compartment if:
   *
   * <ul>
   *   <li>They are identifiers (format: {patientId}.identifier.{system}:{value})
   *   <li>Their resource type has a param key in the compartment definition
   * </ul>
   */
  private boolean isInPatientCompartment(String resourceId) {
    return extractResourceType(resourceId)
        .map(
            resourceType -> {
              // "identifier" entries are patient-related
              if ("identifier".equals(resourceType)) {
                return true;
              }
              return patientCompartment.isInPatientCompartment(resourceType);
            })
        .orElse(true); // Default to compartment if we can't parse the ID
  }

  /**
   * Extracts the resource type from a namespaced resource ID.
   *
   * @param resourceId format: {patientId}.{ResourceType}:{id} or
   *     {patientId}.identifier.{system}:{value}
   * @return the resource type, or empty if the ID doesn't match the expected format
   */
  static Optional<String> extractResourceType(String resourceId) {
    Matcher matcher = RESOURCE_ID_PATTERN.matcher(resourceId);
    if (matcher.find()) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
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

    // Filter transport mapping for compartment IDs only (for salt-based hashing)
    Map<String, String> compartmentTransportMapping =
        transportMapping.entrySet().stream()
            .filter(e -> compartmentIds.inCompartment().contains(e.getKey()))
            .collect(toMap(Entry::getKey, Entry::getValue));

    // Filter transport mapping for non-compartment IDs (for gPAS pseudonyms)
    Map<String, String> nonCompartmentTransportMapping =
        transportMapping.entrySet().stream()
            .filter(e -> compartmentIds.outsideCompartment().contains(e.getKey()))
            .collect(toMap(Entry::getKey, Entry::getValue));

    var resolveMap =
        ImmutableMap.<String, String>builder()
            // Compartment IDs: use salt-based hashing
            .putAll(generateSecureMapping(data.salt(), compartmentTransportMapping))
            // Non-compartment IDs: use gPAS pseudonyms directly
            .putAll(
                generateNonCompartmentMapping(
                    nonCompartmentTransportMapping, nonCompartmentPseudonyms))
            // Patient identifier: use gPAS pseudonym
            .putAll(
                patientIdPseudonyms(
                    r.patientId(),
                    r.patientIdentifierSystem(),
                    data.patientIdPseudonym(),
                    transportMapping))
            .put("dateShiftMillis", valueOf(dateShifts.rdDateShift().toMillis()))
            .buildKeepingLast();

    return rMap.putAll(resolveMap).thenReturn(dateShifts.cdDateShift());
  }

  /**
   * Saves the research mapping in redis for later use by the rda.
   *
   * <p>This method is kept for backward compatibility with existing tests. New code should use
   * generateTransportMapping which handles compartment-aware ID processing.
   */
  static Function<PseudonymData, Mono<Duration>> saveSecureMapping(
      TransportMappingRequest r,
      Map<String, String> transportMapping,
      RMapReactive<String, String> rMap) {
    return data -> {
      var dateShifts = generate(data.dateShiftSeed(), r.maxDateShift(), r.dateShiftPreserve());
      var resolveMap =
          ImmutableMap.<String, String>builder()
              .putAll(generateSecureMapping(data.salt(), transportMapping))
              .putAll(
                  patientIdPseudonyms(
                      r.patientId(),
                      r.patientIdentifierSystem(),
                      data.patientIdPseudonym(),
                      transportMapping))
              .put("dateShiftMillis", valueOf(dateShifts.rdDateShift().toMillis()))
              .buildKeepingLast();
      return rMap.putAll(resolveMap).thenReturn(dateShifts.cdDateShift());
    };
  }

  /**
   * Creates the mapping from transport IDs to gPAS pseudonyms for non-compartment resources.
   *
   * @param transportMapping map from original ID to transport ID
   * @param gpasPseudonyms map from original ID to gPAS pseudonym
   * @return map from transport ID to gPAS pseudonym
   */
  static Map<String, String> generateNonCompartmentMapping(
      Map<String, String> transportMapping, Map<String, String> gpasPseudonyms) {
    return transportMapping.entrySet().stream()
        .filter(e -> gpasPseudonyms.containsKey(e.getKey()))
        .collect(toMap(Entry::getValue, e -> gpasPseudonyms.get(e.getKey())));
  }

  /** generate ids for all entries in the transport mapping */
  static Map<String, String> generateSecureMapping(
      String transportSalt, Map<String, String> transportMapping) {
    return transportMapping.entrySet().stream()
        .collect(toMap(Entry::getValue, entry -> transportHash(transportSalt, entry.getKey())));
  }

  /** hash a transport id using the salt */
  private static String transportHash(String transportSalt, String id) {
    return hashFn.hashString(transportSalt + id, StandardCharsets.UTF_8).toString();
  }

  /**
   * With this function we make sure that the patient's ID in the RDA is the de-identified ID stored
   * in gPAS. This ensures that we can re-identify patients.
   */
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
