package care.smith.fts.tca.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import care.smith.fts.tca.deidentification.configuration.TransportMappingConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.BatchResult;
import org.redisson.api.RBatchReactive;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RBucketsReactive;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TransportIdServiceTest {

  @Mock private RedissonClient redisClient;
  @Mock private RedissonReactiveClient reactiveClient;
  @Mock private RMapCacheReactive<String, String> mapCache;
  @Mock private RBucketReactive<String> bucket;
  @Mock private RBatchReactive batch;
  @Mock private RBucketsReactive buckets;

  private TransportIdService service;
  private MeterRegistry meterRegistry;
  private RandomGenerator randomGenerator;
  private Duration defaultTtl;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    randomGenerator = new SecureRandom();
    defaultTtl = Duration.ofMinutes(10);

    var config = new TransportMappingConfiguration();
    config.setTtl(defaultTtl);

    lenient().when(redisClient.reactive()).thenReturn(reactiveClient);
    lenient().when(reactiveClient.<String, String>getMapCache(anyString())).thenReturn(mapCache);
    lenient().when(reactiveClient.<String>getBucket(anyString())).thenReturn(bucket);
    lenient().when(reactiveClient.createBatch()).thenReturn(batch);
    lenient().when(reactiveClient.getBuckets()).thenReturn(buckets);

    service = new TransportIdService(redisClient, config, meterRegistry, randomGenerator);
  }

  @Test
  void generateIdReturns32CharBase64() {
    var id = service.generateId();

    assertThat(id).hasSize(32);
    assertThat(id).matches("[A-Za-z0-9_-]+");
  }

  @Test
  void generatedIdsAreUnique() {
    var id1 = service.generateId();
    var id2 = service.generateId();
    var id3 = service.generateId();

    assertThat(id1).isNotEqualTo(id2);
    assertThat(id1).isNotEqualTo(id3);
    assertThat(id2).isNotEqualTo(id3);
  }

  @Test
  void getDefaultTtlReturnsConfiguredValue() {
    assertThat(service.getDefaultTtl()).isEqualTo(defaultTtl);
  }

  @Test
  void storeAllMappingsStoresInRedis() {
    when(mapCache.expire(any(Duration.class))).thenReturn(Mono.just(true));
    when(mapCache.putAll(any())).thenReturn(Mono.empty());

    var allData = Map.of("tId-1", "sId-1", "tId-2", "sId-2", "dateShiftMillis", "1000");
    var result = service.storeAllMappings("transfer-1", allData, defaultTtl);

    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void fetchAllMappingsRetrievesFromRedis() {
    var allMappings = Map.of("tId-1", "sId-1", "tId-2", "sId-2", "dateShiftMillis", "1000");
    when(mapCache.readAllMap()).thenReturn(Mono.just(allMappings));

    var result = service.fetchAllMappings("transfer-1");

    StepVerifier.create(result)
        .assertNext(
            retrieved -> {
              assertThat(retrieved).hasSize(3);
              assertThat(retrieved).containsEntry("tId-1", "sId-1");
              assertThat(retrieved).containsEntry("tId-2", "sId-2");
              assertThat(retrieved).containsEntry("dateShiftMillis", "1000");
            })
        .verifyComplete();
  }

  @Test
  void acceptsValidBase64UrlTransferId() {
    when(mapCache.readAllMap()).thenReturn(Mono.just(Map.of()));

    var result = service.fetchAllMappings("validBase64Url_transfer-Id123");

    StepVerifier.create(result).expectNext(Map.of()).verifyComplete();
  }

  @Test
  void storeMappingStoresDirectlyInRedis() {
    when(bucket.set(eq("sId-1"), eq(defaultTtl))).thenReturn(Mono.empty());

    var result = service.storeMapping("tId-1", "sId-1", defaultTtl);

    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void fetchMappingRetrievesFromRedis() {
    when(bucket.get()).thenReturn(Mono.just("sId-1"));

    var result = service.fetchMapping("tId-1");

    StepVerifier.create(result).expectNext("sId-1").verifyComplete();
  }

  @Test
  void fetchMappingReturnsEmptyWhenNotFound() {
    when(bucket.get()).thenReturn(Mono.empty());

    var result = service.fetchMapping("nonexistent-tid");

    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void storeMappingsStoresMultipleMappings() {
    @SuppressWarnings("unchecked")
    RBucketReactive<String> batchBucket =
        (RBucketReactive<String>) org.mockito.Mockito.mock(RBucketReactive.class);
    when(batch.<String>getBucket(anyString())).thenReturn(batchBucket);
    when(batchBucket.set(anyString(), any(Duration.class))).thenReturn(Mono.empty());
    when(batch.execute()).thenReturn(Mono.just(new BatchResult<>(java.util.List.of(), 0)));

    var mappings = Map.of("tId-1", "sId-1", "tId-2", "sId-2");
    var result = service.storeMappings(mappings, defaultTtl);

    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void storeMappingsWithEmptyMapCompletesImmediately() {
    var result = service.storeMappings(Map.of(), defaultTtl);

    StepVerifier.create(result).verifyComplete();
    // No Redis interactions should occur - batch.execute() would fail if called without setup
  }

  @Test
  void fetchMappingsRetrievesMultipleMappings() {
    // RBuckets.get() returns map with full keys (including prefix)
    when(buckets.<String>get(any(String[].class)))
        .thenReturn(Mono.just(Map.of("tid:tId-1", "sId-1", "tid:tId-2", "sId-2")));

    var result = service.fetchMappings(Set.of("tId-1", "tId-2"));

    StepVerifier.create(result)
        .assertNext(
            retrieved -> {
              assertThat(retrieved).hasSize(2);
              assertThat(retrieved).containsEntry("tId-1", "sId-1");
              assertThat(retrieved).containsEntry("tId-2", "sId-2");
            })
        .verifyComplete();
  }

  @Test
  void fetchMappingsWithEmptySetReturnsEmptyMap() {
    var result = service.fetchMappings(Set.of());

    StepVerifier.create(result).expectNext(Map.of()).verifyComplete();
    // No Redis interactions should occur - buckets.get() would fail if called without setup
  }

  @Test
  void fetchMappingsExcludesNotFoundMappings() {
    // RBuckets.get() only returns keys that exist - missing keys are not in the result map
    when(buckets.<String>get(any(String[].class)))
        .thenReturn(Mono.just(Map.of("tid:tId-1", "sId-1")));

    var result = service.fetchMappings(Set.of("tId-1", "tId-2"));

    StepVerifier.create(result)
        .assertNext(
            retrieved -> {
              assertThat(retrieved).hasSize(1);
              assertThat(retrieved).containsEntry("tId-1", "sId-1");
            })
        .verifyComplete();
  }
}
