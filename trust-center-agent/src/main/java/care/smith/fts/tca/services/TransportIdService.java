package care.smith.fts.tca.services;

import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;

import care.smith.fts.tca.deidentification.configuration.TransportMappingConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
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
  private static final String TID_KEY_PREFIX = "tid:";
  private static final String DATESHIFT_KEY_PREFIX = "dateshift:";

  private final RandomGenerator randomGenerator;
  private final RedissonClient redisClient;
  private final Duration ttl;
  private final MeterRegistry meterRegistry;

  public TransportIdService(
      RedissonClient redisClient,
      TransportMappingConfiguration config,
      MeterRegistry meterRegistry,
      RandomGenerator randomGenerator) {
    this.randomGenerator = randomGenerator;
    this.redisClient = redisClient;
    this.ttl = config.getTtl();
    this.meterRegistry = meterRegistry;
  }

  /**
   * Generates a cryptographically secure random ID (32-char Base64URL).
   *
   * <p>Used for both transfer session IDs and individual transport IDs.
   *
   * @return a 32-character Base64URL-encoded ID
   */
  public String generateId() {
    byte[] bytes = new byte[ID_BYTES];
    randomGenerator.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Stores all mappings in a single batch operation with TTL applied at map level.
   *
   * <p>This method is compatible with the FhirMappingProvider pattern where all data (mappings and
   * metadata like dateShiftMillis) is stored in one operation.
   *
   * @param transferId the transfer session identifier
   * @param allData map containing all data to store (mappings + metadata)
   * @param ttl time-to-live for the entire map
   * @return Mono completing when storage is done
   */
  public Mono<Void> storeAllMappings(String transferId, Map<String, String> allData, Duration ttl) {
    return Mono.defer(
        () -> {
          var mapCache = getMapCache(transferId);
          return mapCache
              .expire(ttl)
              .then(mapCache.putAll(allData))
              .retryWhen(defaultRetryStrategy(meterRegistry, "storeAllMappings"))
              .doOnSuccess(
                  v -> log.trace("Stored {} entries: transferId={}", allData.size(), transferId));
        });
  }

  /**
   * Retrieves all data for a transfer session including metadata.
   *
   * @param transferId the transfer session identifier
   * @return Mono emitting all stored data for this session
   */
  public Mono<Map<String, String>> fetchAllMappings(String transferId) {
    return Mono.defer(
        () -> {
          var mapCache = getMapCache(transferId);
          return mapCache
              .readAllMap()
              .retryWhen(defaultRetryStrategy(meterRegistry, "fetchAllMappings"))
              .doOnSuccess(
                  m -> log.trace("Fetched {} entries: transferId={}", m.size(), transferId));
        });
  }

  // ========== Direct tid→sid storage (without transferId grouping) ==========

  /**
   * Stores a single transport ID → secure pseudonym mapping directly in Redis.
   *
   * <p>Unlike the transferId-based methods, this stores each tid as a separate Redis key, enabling
   * direct lookup without requiring a session identifier. Uses the configured TTL.
   *
   * @param tid the transport ID (key)
   * @param sid the secure pseudonym (value)
   * @return Mono completing when storage is done
   */
  public Mono<Void> storeMapping(String tid, String sid) {
    return Mono.defer(
        () -> {
          var bucket = redisClient.reactive().<String>getBucket(tidKey(tid));
          return bucket
              .set(sid, ttl)
              .retryWhen(defaultRetryStrategy(meterRegistry, "storeMapping"))
              .doOnSuccess(v -> log.trace("Stored direct mapping: tid={}", tid));
        });
  }

  /**
   * Fetches the secure pseudonym for a given transport ID.
   *
   * @param tid the transport ID to look up
   * @return Mono emitting the sid, or empty if not found
   */
  public Mono<String> fetchMapping(String tid) {
    return Mono.defer(
        () -> {
          var bucket = redisClient.reactive().<String>getBucket(tidKey(tid));
          return bucket
              .get()
              .retryWhen(defaultRetryStrategy(meterRegistry, "fetchMapping"))
              .doOnSuccess(sid -> log.trace("Fetched mapping: tid={}, found={}", tid, sid != null));
        });
  }

  /**
   * Stores multiple tid→sid mappings directly in Redis using pipelined batch execution.
   *
   * @param tidToSid map of transport IDs to secure pseudonyms
   * @param ttl time-to-live for all mappings
   * @return Mono completing when all mappings are stored
   */
  public Mono<Void> storeMappings(Map<String, String> tidToSid, Duration ttl) {
    if (tidToSid.isEmpty()) {
      return Mono.empty();
    }
    return Mono.defer(
        () -> {
          var batch = redisClient.reactive().createBatch();
          tidToSid.forEach(
              (tid, sid) -> {
                batch.<String>getBucket(tidKey(tid)).set(sid, ttl);
              });
          return batch
              .execute()
              .retryWhen(defaultRetryStrategy(meterRegistry, "storeMappings"))
              .doOnSuccess(v -> log.trace("Stored {} direct mappings", tidToSid.size()))
              .then();
        });
  }

  /**
   * Fetches secure pseudonyms for multiple transport IDs using Redis MGET.
   *
   * <p>Missing or expired mappings are silently filtered from the result. The returned map only
   * contains entries where a valid sid was found.
   *
   * @param tids set of transport IDs to look up
   * @return Mono emitting map of tid→sid (only includes found mappings, never null values)
   */
  public Mono<Map<String, String>> fetchMappings(Set<String> tids) {
    if (tids.isEmpty()) {
      return Mono.just(Map.of());
    }
    return Mono.defer(
        () -> {
          var keys = tids.stream().map(this::tidKey).toArray(String[]::new);
          return redisClient
              .reactive()
              .getBuckets()
              .<String>get(keys)
              .retryWhen(defaultRetryStrategy(meterRegistry, "fetchMappings"))
              .map(
                  result ->
                      result.entrySet().stream()
                          .collect(
                              Collectors.toMap(
                                  e -> e.getKey().substring(TID_KEY_PREFIX.length()),
                                  Map.Entry::getValue)))
              .doOnSuccess(
                  m -> log.trace("Fetched {} of {} requested mappings", m.size(), tids.size()));
        });
  }

  private String tidKey(String tid) {
    return TID_KEY_PREFIX + tid;
  }

  private String dateShiftKey(String transferId) {
    return DATESHIFT_KEY_PREFIX + transferId;
  }

  private RMapCacheReactive<String, String> getMapCache(String transferId) {
    return redisClient.reactive().getMapCache(transferId);
  }

  /**
   * Stores the RDA date shift value for a transfer session.
   *
   * <p>This is used by the FHIR Pseudonymizer date shift endpoint to store the RDA's portion of the
   * date shift, which will be retrieved when RDA processes the bundle.
   *
   * @param transferId the transfer session identifier
   * @param rdDateShiftDays the RDA date shift in days
   * @param ttl time-to-live for this mapping
   * @return Mono completing when storage is done
   */
  public Mono<Void> storeDateShift(String transferId, int rdDateShiftDays, Duration ttl) {
    return Mono.defer(
        () -> {
          var bucket = redisClient.reactive().<Integer>getBucket(dateShiftKey(transferId));
          return bucket
              .set(rdDateShiftDays, ttl)
              .retryWhen(defaultRetryStrategy(meterRegistry, "storeDateShift"))
              .doOnSuccess(
                  v ->
                      log.trace(
                          "Stored date shift: transferId={}, days={}",
                          transferId,
                          rdDateShiftDays));
        });
  }

  /**
   * Fetches the stored RDA date shift value for a transfer session.
   *
   * @param transferId the transfer session identifier
   * @return Mono emitting the date shift in days, or empty if not found
   */
  public Mono<Integer> fetchDateShift(String transferId) {
    return Mono.defer(
        () -> {
          var bucket = redisClient.reactive().<Integer>getBucket(dateShiftKey(transferId));
          return bucket
              .get()
              .retryWhen(defaultRetryStrategy(meterRegistry, "fetchDateShift"))
              .doOnSuccess(
                  days ->
                      log.trace(
                          "Fetched date shift: transferId={}, found={}", transferId, days != null));
        });
  }
}
