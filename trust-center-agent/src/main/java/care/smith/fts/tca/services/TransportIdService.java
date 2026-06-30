package care.smith.fts.tca.services;

import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;

import care.smith.fts.tca.deidentification.configuration.TransportMappingConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for generating and managing transport IDs.
 *
 * <p>Transport IDs (tIDs) are temporary identifiers used to replace real pseudonyms during data
 * transfer from CDA to RDA. The tID→sID mappings are stored in Redis with a configurable TTL.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li>Generate cryptographically secure transport IDs (32 chars, Base64URL)
 *   <li>Store tID→sID mappings in Redis grouped by transfer session
 *   <li>Resolve transport IDs back to secure pseudonyms for RDA
 *   <li>Manage date shift values per transfer session
 * </ul>
 */
@Slf4j
@Service
public class TransportIdService {

  private static final int ID_BYTES = 24; // 24 bytes = 32 Base64URL chars
  private static final String DATE_SHIFT_KEY = "_dateShiftMillis";
  private static final String KEY_PREFIX = "transport-mapping:";

  private final SecureRandom secureRandom;
  private final RedissonClient redisClient;
  private final Duration defaultTtl;
  private final MeterRegistry meterRegistry;

  public TransportIdService(
      RedissonClient redisClient,
      TransportMappingConfiguration config,
      MeterRegistry meterRegistry) {
    this.secureRandom = new SecureRandom();
    this.redisClient = redisClient;
    this.defaultTtl = config.getTtl();
    this.meterRegistry = meterRegistry;
  }

  /**
   * Generates a cryptographically secure transport ID.
   *
   * @return a 32-character Base64URL-encoded transport ID
   */
  public String generateTransportId() {
    byte[] bytes = new byte[ID_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Generates a new transfer session ID.
   *
   * @return a unique transfer session identifier
   */
  public String generateTransferId() {
    return generateTransportId();
  }

  /**
   * Stores a single tID→sID mapping in Redis.
   *
   * @param transferId the transfer session identifier
   * @param transportId the transport ID (tID)
   * @param securePseudonym the real pseudonym (sID)
   * @param domain the pseudonymization domain
   * @param ttl time-to-live for this mapping
   * @return Mono emitting the stored transport ID
   */
  public Mono<String> storeMapping(
      String transferId, String transportId, String securePseudonym, String domain, Duration ttl) {
    var mapCache = getMapCache(transferId);
    return mapCache
        .fastPut(transportId, securePseudonym, ttl.toMillis(), TimeUnit.MILLISECONDS)
        .retryWhen(defaultRetryStrategy(meterRegistry, "storeTransportMapping"))
        .doOnSuccess(
            v ->
                log.trace(
                    "Stored mapping: transferId={}, tID={} (domain={})",
                    transferId,
                    transportId,
                    domain))
        .thenReturn(transportId);
  }

  /**
   * Stores multiple tID→sID mappings in Redis.
   *
   * @param transferId the transfer session identifier
   * @param mappings map from transport ID to secure pseudonym
   * @param domain the pseudonymization domain
   * @param ttl time-to-live for these mappings
   * @return Mono emitting the stored mappings
   */
  public Mono<Map<String, String>> storeMappings(
      String transferId, Map<String, String> mappings, String domain, Duration ttl) {
    if (mappings.isEmpty()) {
      return Mono.just(Map.of());
    }

    var mapCache = getMapCache(transferId);
    return Flux.fromIterable(mappings.entrySet())
        .flatMap(
            entry ->
                mapCache
                    .fastPut(
                        entry.getKey(), entry.getValue(), ttl.toMillis(), TimeUnit.MILLISECONDS)
                    .thenReturn(entry))
        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
        .retryWhen(defaultRetryStrategy(meterRegistry, "storeTransportMappings"))
        .doOnSuccess(
            m ->
                log.trace(
                    "Stored {} mappings: transferId={} (domain={})", m.size(), transferId, domain));
  }

  /**
   * Resolves transport IDs to their corresponding secure pseudonyms.
   *
   * @param transferId the transfer session identifier
   * @param transportIds the set of transport IDs to resolve
   * @return Mono emitting a map from tID to sID (only for found mappings)
   */
  public Mono<Map<String, String>> resolveMappings(String transferId, Set<String> transportIds) {
    if (transportIds.isEmpty()) {
      return Mono.just(Map.of());
    }

    var mapCache = getMapCache(transferId);
    return Flux.fromIterable(transportIds)
        .flatMap(
            tId ->
                mapCache
                    .get(tId)
                    .map(sId -> Map.entry(tId, sId))
                    .defaultIfEmpty(Map.entry(tId, "")))
        .filter(entry -> !entry.getValue().isEmpty())
        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
        .retryWhen(defaultRetryStrategy(meterRegistry, "resolveTransportMappings"))
        .doOnSuccess(
            m ->
                log.trace(
                    "Resolved {} of {} mappings for transferId={}",
                    m.size(),
                    transportIds.size(),
                    transferId));
  }

  /**
   * Retrieves all mappings for a transfer session.
   *
   * @param transferId the transfer session identifier
   * @return Mono emitting all tID→sID mappings for this session
   */
  public Mono<Map<String, String>> getAllMappings(String transferId) {
    var mapCache = getMapCache(transferId);
    return mapCache
        .readAllMap()
        .retryWhen(defaultRetryStrategy(meterRegistry, "getAllTransportMappings"))
        .map(
            m -> {
              var result = new HashMap<>(m);
              result.remove(DATE_SHIFT_KEY); // Exclude metadata
              return Map.copyOf(result);
            })
        .doOnSuccess(
            m -> log.trace("Retrieved {} mappings for transferId={}", m.size(), transferId));
  }

  /**
   * Stores the date shift value for a transfer session.
   *
   * @param transferId the transfer session identifier
   * @param dateShiftMillis the date shift value in milliseconds
   * @param ttl time-to-live for this value
   * @return Mono emitting the stored date shift value
   */
  public Mono<Long> storeDateShiftValue(String transferId, long dateShiftMillis, Duration ttl) {
    var mapCache = getMapCache(transferId);
    return mapCache
        .fastPut(
            DATE_SHIFT_KEY, String.valueOf(dateShiftMillis), ttl.toMillis(), TimeUnit.MILLISECONDS)
        .retryWhen(defaultRetryStrategy(meterRegistry, "storeDateShiftValue"))
        .doOnSuccess(
            v ->
                log.trace("Stored dateShift: transferId={}, value={}", transferId, dateShiftMillis))
        .thenReturn(dateShiftMillis);
  }

  /**
   * Retrieves the date shift value for a transfer session.
   *
   * @param transferId the transfer session identifier
   * @return Mono emitting the date shift value, or empty if not found
   */
  public Mono<Long> getDateShiftValue(String transferId) {
    var mapCache = getMapCache(transferId);
    return mapCache
        .get(DATE_SHIFT_KEY)
        .flatMap(
            value -> {
              try {
                return Mono.just(Long.parseLong(value));
              } catch (NumberFormatException e) {
                log.warn("Invalid dateShift value for transferId={}: {}", transferId, value);
                return Mono.empty();
              }
            })
        .retryWhen(defaultRetryStrategy(meterRegistry, "getDateShiftValue"))
        .doOnSuccess(v -> log.trace("Retrieved dateShift: transferId={}, value={}", transferId, v));
  }

  /**
   * Gets the default TTL for transport mappings.
   *
   * @return the default TTL duration
   */
  public Duration getDefaultTtl() {
    return defaultTtl;
  }

  private RMapCacheReactive<String, String> getMapCache(String transferId) {
    return redisClient.reactive().getMapCache(KEY_PREFIX + transferId);
  }
}
