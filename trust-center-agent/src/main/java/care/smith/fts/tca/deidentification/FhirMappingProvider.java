package care.smith.fts.tca.deidentification;

import static care.smith.fts.tca.deidentification.DateShiftUtil.generate;
import static java.lang.String.valueOf;
import static java.util.Set.of;
import static java.util.stream.Collectors.toMap;

import care.smith.fts.tca.deidentification.configuration.TransportMappingConfiguration;
import care.smith.fts.tca.services.TransportIdService;
import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import care.smith.fts.util.tca.SecureMappingResponse;
import care.smith.fts.util.tca.TcaDomains;
import care.smith.fts.util.tca.TransportMappingRequest;
import care.smith.fts.util.tca.TransportMappingResponse;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class FhirMappingProvider implements MappingProvider {
  private static final HashFunction hashFn = Hashing.sha256();

  record PseudonymData(String patientIdPseudonym, String salt, String dateShiftSeed) {}

  private final GpasClient gpasClient;
  private final TransportMappingConfiguration configuration;
  private final TransportIdService transportIdService;

  public FhirMappingProvider(
      GpasClient gpasClient,
      TransportMappingConfiguration configuration,
      TransportIdService transportIdService) {
    this.gpasClient = gpasClient;
    this.configuration = configuration;
    this.transportIdService = transportIdService;
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
    var transferId = transportIdService.generateId();
    var transportMapping =
        r.resourceIds().stream().collect(toMap(id -> id, id -> transportIdService.generateId()));
    return fetchPseudonymAndSalts(r.patientId(), r.tcaDomains(), r.maxDateShift())
        .flatMap(data -> saveSecureMapping(r, transportMapping, data, transferId))
        .map(cdShift -> new TransportMappingResponse(transferId, transportMapping, cdShift));
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

  /** Saves the research mapping in redis for later use by the rda. */
  private Mono<Duration> saveSecureMapping(
      TransportMappingRequest r,
      Map<String, String> transportMapping,
      PseudonymData data,
      String transferId) {
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
    return transportIdService
        .storeAllMappings(transferId, resolveMap, configuration.getTtl())
        .thenReturn(dateShifts.cdDateShift());
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
    return transportIdService
        .fetchAllMappings(transferId)
        .map(SecureMappingResponse::buildResolveResponse);
  }
}
