package care.smith.fts.tca.deidentification;

import static care.smith.fts.tca.deidentification.DateShiftUtil.generate;
import static care.smith.fts.tca.deidentification.DateShiftUtil.shiftDate;
import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_PREFIX;
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

  record PseudonymData(String patientIdentifierPseudonym, String salt, String dateShiftSeed) {}

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
        "Generate transport mapping for patientIdentifier={}, {} IDs, {} date tIDs",
        r.patientIdentifier(),
        r.resourceIds().size(),
        r.dateTransportMappings().size());

    var transferId = transportIdService.generateId();
    var transportMapping =
        r.resourceIds().stream().collect(toMap(id -> id, id -> transportIdService.generateId()));

    return fetchPseudonymAndSalts(r.patientIdentifier(), r.tcaDomains(), r.maxDateShift())
        .flatMap(
            data -> {
              var dateShift =
                  generate(data.dateShiftSeed(), r.maxDateShift(), r.dateShiftPreserve());
              // Compute tID→shiftedDate from tID→originalDate
              var tidToShiftedDate = computeTidToShiftedDate(r.dateTransportMappings(), dateShift);

              return saveSecureMapping(r, data, transportMapping, tidToShiftedDate, transferId)
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

  /** Saves the research mapping in redis for later use by the RDA. */
  private Mono<Void> saveSecureMapping(
      TransportMappingRequest r,
      PseudonymData data,
      Map<String, String> transportMapping,
      Map<String, String> tidToShiftedDate,
      String transferId) {

    var resolveMapBuilder =
        ImmutableMap.<String, String>builder()
            .putAll(generateSecureMapping(data.salt(), transportMapping))
            .putAll(
                patientIdentifierPseudonyms(
                    r.patientIdentifier(),
                    r.patientIdentifierSystem(),
                    data.patientIdentifierPseudonym(),
                    transportMapping));

    // Store tID→shiftedDate mappings with prefix for RDA to resolve
    tidToShiftedDate.forEach(
        (tId, shiftedDate) -> resolveMapBuilder.put(DATE_SHIFT_PREFIX + tId, shiftedDate));

    return transportIdService
        .storeAllMappings(transferId, resolveMapBuilder.buildKeepingLast(), configuration.getTtl());
  }

  static Map<String, String> generateSecureMapping(
      String transportSalt, Map<String, String> transportMapping) {
    return transportMapping.entrySet().stream()
        .collect(toMap(Entry::getValue, entry -> transportHash(transportSalt, entry.getKey())));
  }

  private static String transportHash(String transportSalt, String id) {
    return hashFn.hashString(transportSalt + id, StandardCharsets.UTF_8).toString();
  }

  /**
   * With this function we make sure that the patient's identifier in the RDA is the de-identified
   * identifier stored in gPAS. This ensures that we can re-identify patients.
   */
  static Map<String, String> patientIdentifierPseudonyms(
      String patientIdentifier,
      String patientIdentifierSystem,
      String patientIdentifierPseudonym,
      Map<String, String> transportMapping) {
    var x = NamespacingReplacementProvider.withNamespacing(patientIdentifier);
    var name = x.getKeyForSystemAndValue(patientIdentifierSystem, patientIdentifier);

    return transportMapping.entrySet().stream()
        .filter(entry -> entry.getKey().equals(name))
        .collect(toMap(Entry::getValue, id -> patientIdentifierPseudonym));
  }

  @Override
  public Mono<SecureMappingResponse> fetchSecureMapping(String transferId) {
    return transportIdService
        .fetchAllMappings(transferId)
        .map(SecureMappingResponse::buildResolveResponse);
  }
}
