package care.smith.fts.tca.deidentification;

import static care.smith.fts.tca.deidentification.DateShiftUtil.generate;
import static care.smith.fts.tca.deidentification.DateShiftUtil.shiftDate;
import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_PREFIX;
import static java.util.stream.Collectors.toMap;

import care.smith.fts.tca.deidentification.configuration.TransportMappingConfiguration;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import care.smith.fts.util.tca.SecureMappingResponse;
import care.smith.fts.util.tca.TransportMappingRequest;
import care.smith.fts.util.tca.TransportMappingResponse;
import care.smith.fts.util.tca.TransportMappingsRequest;
import care.smith.fts.util.tca.TransportMappingsResponse;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class FhirMappingProvider implements MappingProvider {
  private static final HashFunction hashFn = Hashing.sha256();

  record PseudonymData(String patientIdentifierPseudonym, String salt, String dateShiftSeed) {}

  private final GpasClient gpasClient;
  private final TransportMappingConfiguration configuration;
  private final RedissonClient redisClient;
  private final RetryStrategy retryStrategy;
  private final RandomStringGenerator randomStringGenerator;

  public FhirMappingProvider(
      GpasClient gpasClient,
      RedissonClient redisClient,
      TransportMappingConfiguration configuration,
      RetryStrategy retryStrategy,
      RandomStringGenerator randomStringGenerator) {
    this.gpasClient = gpasClient;
    this.configuration = configuration;
    this.redisClient = redisClient;
    this.retryStrategy = retryStrategy;
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
    return generateTransportMappings(new TransportMappingsRequest(List.of(r)))
        .map(response -> new TransportMappingResponse(response.transferIds().getFirst()));
  }

  @Override
  public Mono<TransportMappingsResponse> generateTransportMappings(TransportMappingsRequest r) {
    var requests = r.requests();
    if (requests.isEmpty()) {
      return Mono.just(new TransportMappingsResponse(List.of()));
    }
    log.trace("Store transport mappings for {} requests", requests.size());
    // flatMapSequential keeps the transferIds aligned with the request order: a single patient may
    // contribute several bundles (paginated $everything), so each request gets its own transferId.
    return fetchPseudonyms(requests)
        .flatMap(
            pseudonymsByPatient ->
                Flux.fromIterable(requests)
                    .flatMapSequential(
                        req -> storeMapping(req, pseudonymsByPatient.get(req.patientIdentifier())))
                    .collectList())
        .map(TransportMappingsResponse::new);
  }

  private Mono<String> storeMapping(TransportMappingRequest r, PseudonymData data) {
    var transferId = randomStringGenerator.generate();
    var sMap = redisClient.reactive().<String, String>getMapCache(transferId);
    var dateShift = generate(data.dateShiftSeed(), r.maxDateShift(), r.dateShiftPreserve());
    var tidToShiftedDate = computeTidToShiftedDate(r.dateMappings(), dateShift);
    return sMap.expire(configuration.getTtl())
        .then(saveSecureMapping(r, data, tidToShiftedDate, sMap))
        .thenReturn(transferId);
  }

  private Map<String, String> computeTidToShiftedDate(
      Map<String, String> dateMappings, Duration dateShift) {
    return dateMappings.entrySet().stream()
        .collect(toMap(Entry::getKey, e -> shiftDate(e.getValue(), dateShift)));
  }

  /**
   * Fetches or creates all pseudonyms for a batch of patients, issuing a single gPAS call per
   * distinct domain. The pseudonym, salt and dateShift keys of every patient are grouped by their
   * target domain, so domains shared across patients (and across the three key types) collapse into
   * one {@code $pseudonymizeAllowCreate} request instead of one call per key per patient.
   */
  private Mono<Map<String, PseudonymData>> fetchPseudonyms(List<TransportMappingRequest> requests) {
    Map<String, Set<String>> keysByDomain = new HashMap<>();
    for (var r : requests) {
      var domains = r.tcaDomains();
      keysByDomain
          .computeIfAbsent(domains.pseudonym(), d -> new HashSet<>())
          .add(r.patientIdentifier());
      keysByDomain.computeIfAbsent(domains.salt(), d -> new HashSet<>()).add(saltKey(r));
      keysByDomain.computeIfAbsent(domains.dateShift(), d -> new HashSet<>()).add(dateShiftKey(r));
    }
    return Flux.fromIterable(keysByDomain.entrySet())
        .flatMap(
            e ->
                gpasClient
                    .fetchOrCreatePseudonyms(e.getKey(), e.getValue())
                    .map(pseudonyms -> Map.entry(e.getKey(), pseudonyms)))
        .collectMap(Entry::getKey, Entry::getValue)
        .map(byDomain -> pseudonymDataByPatient(requests, byDomain));
  }

  private static Map<String, PseudonymData> pseudonymDataByPatient(
      List<TransportMappingRequest> requests, Map<String, Map<String, String>> byDomain) {
    Map<String, PseudonymData> byPatient = new HashMap<>();
    for (var r : requests) {
      var domains = r.tcaDomains();
      byPatient.put(
          r.patientIdentifier(),
          new PseudonymData(
              byDomain.get(domains.pseudonym()).get(r.patientIdentifier()),
              byDomain.get(domains.salt()).get(saltKey(r)),
              byDomain.get(domains.dateShift()).get(dateShiftKey(r))));
    }
    return byPatient;
  }

  private static String saltKey(TransportMappingRequest r) {
    return "Salt_" + r.patientIdentifier();
  }

  private static String dateShiftKey(TransportMappingRequest r) {
    return "%s_%s".formatted(r.maxDateShift().toString(), r.patientIdentifier());
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
        .retryWhen(retryStrategy.forRequest("fetchSecureMapping"))
        .map(SecureMappingResponse::buildResolveResponse);
  }
}
