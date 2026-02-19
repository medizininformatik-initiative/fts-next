package care.smith.fts.tca.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import care.smith.fts.tca.deidentification.configuration.TransportMappingConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

  private TransportIdService service;
  private MeterRegistry meterRegistry;
  private Duration defaultTtl;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    defaultTtl = Duration.ofMinutes(10);

    var config = new TransportMappingConfiguration();
    config.setTtl(defaultTtl);

    lenient().when(redisClient.reactive()).thenReturn(reactiveClient);
    lenient().when(reactiveClient.<String, String>getMapCache(anyString())).thenReturn(mapCache);

    service = new TransportIdService(redisClient, config, meterRegistry);
  }

  @Test
  void generateTransportIdReturns32CharBase64() {
    var transportId = service.generateTransportId();

    assertThat(transportId).hasSize(32);
    assertThat(transportId).matches("[A-Za-z0-9_-]+");
  }

  @Test
  void generateTransferIdReturns32CharBase64() {
    var transferId = service.generateTransferId();

    assertThat(transferId).hasSize(32);
    assertThat(transferId).matches("[A-Za-z0-9_-]+");
  }

  @Test
  void generatedIdsAreUnique() {
    var id1 = service.generateTransportId();
    var id2 = service.generateTransportId();
    var id3 = service.generateTransferId();

    assertThat(id1).isNotEqualTo(id2);
    assertThat(id1).isNotEqualTo(id3);
    assertThat(id2).isNotEqualTo(id3);
  }

  @Test
  void getDefaultTtlReturnsConfiguredValue() {
    assertThat(service.getDefaultTtl()).isEqualTo(defaultTtl);
  }

  @Test
  void storeMappingStoresInRedis() {
    when(mapCache.fastPut(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
        .thenReturn(Mono.just(true));

    var result = service.storeMapping("transfer-1", "tId-123", "sId-456", "domain", defaultTtl);

    StepVerifier.create(result).expectNext("tId-123").verifyComplete();
  }

  @Test
  void storeMappingsWithEmptyMapReturnsEmpty() {
    var result = service.storeMappings("transfer-1", Map.of(), "domain", defaultTtl);

    StepVerifier.create(result).expectNext(Map.of()).verifyComplete();
  }

  @Test
  void storeMappingsStoresMultipleInRedis() {
    when(mapCache.fastPut(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
        .thenReturn(Mono.just(true));

    var mappings = Map.of("tId-1", "sId-1", "tId-2", "sId-2");
    var result = service.storeMappings("transfer-1", mappings, "domain", defaultTtl);

    StepVerifier.create(result)
        .assertNext(
            stored -> {
              assertThat(stored).hasSize(2);
              assertThat(stored).containsEntry("tId-1", "sId-1");
              assertThat(stored).containsEntry("tId-2", "sId-2");
            })
        .verifyComplete();
  }

  @Test
  void resolveMappingsWithEmptySetReturnsEmpty() {
    var result = service.resolveMappings("transfer-1", Set.of());

    StepVerifier.create(result).expectNext(Map.of()).verifyComplete();
  }

  @Test
  void resolveMappingsRetrievesFromRedis() {
    when(mapCache.get("tId-1")).thenReturn(Mono.just("sId-1"));
    when(mapCache.get("tId-2")).thenReturn(Mono.just("sId-2"));

    var result = service.resolveMappings("transfer-1", Set.of("tId-1", "tId-2"));

    StepVerifier.create(result)
        .assertNext(
            resolved -> {
              assertThat(resolved).hasSize(2);
              assertThat(resolved).containsEntry("tId-1", "sId-1");
              assertThat(resolved).containsEntry("tId-2", "sId-2");
            })
        .verifyComplete();
  }

  @Test
  void resolveMappingsFiltersNotFound() {
    when(mapCache.get("tId-1")).thenReturn(Mono.just("sId-1"));
    when(mapCache.get("tId-missing")).thenReturn(Mono.empty());

    var result = service.resolveMappings("transfer-1", Set.of("tId-1", "tId-missing"));

    StepVerifier.create(result)
        .assertNext(
            resolved -> {
              assertThat(resolved).hasSize(1);
              assertThat(resolved).containsEntry("tId-1", "sId-1");
              assertThat(resolved).doesNotContainKey("tId-missing");
            })
        .verifyComplete();
  }

  @Test
  void getAllMappingsRetrievesAllFromRedis() {
    var allMappings = Map.of("tId-1", "sId-1", "tId-2", "sId-2", "_dateShiftMillis", "1000");
    when(mapCache.readAllMap()).thenReturn(Mono.just(allMappings));

    var result = service.getAllMappings("transfer-1");

    StepVerifier.create(result)
        .assertNext(
            retrieved -> {
              assertThat(retrieved).hasSize(2);
              assertThat(retrieved).containsEntry("tId-1", "sId-1");
              assertThat(retrieved).containsEntry("tId-2", "sId-2");
              assertThat(retrieved).doesNotContainKey("_dateShiftMillis");
            })
        .verifyComplete();
  }

  @Test
  void storeDateShiftValueStoresInRedis() {
    when(mapCache.fastPut(eq("_dateShiftMillis"), eq("5000"), anyLong(), any(TimeUnit.class)))
        .thenReturn(Mono.just(true));

    var result = service.storeDateShiftValue("transfer-1", 5000L, defaultTtl);

    StepVerifier.create(result).expectNext(5000L).verifyComplete();
  }

  @Test
  void getDateShiftValueRetrievesFromRedis() {
    when(mapCache.get("_dateShiftMillis")).thenReturn(Mono.just("3000"));

    var result = service.getDateShiftValue("transfer-1");

    StepVerifier.create(result).expectNext(3000L).verifyComplete();
  }

  @Test
  void getDateShiftValueReturnsEmptyForNotFound() {
    when(mapCache.get("_dateShiftMillis")).thenReturn(Mono.empty());

    var result = service.getDateShiftValue("transfer-1");

    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void getDateShiftValueReturnsEmptyForInvalidNumber() {
    when(mapCache.get("_dateShiftMillis")).thenReturn(Mono.just("not-a-number"));

    var result = service.getDateShiftValue("transfer-1");

    StepVerifier.create(result).verifyComplete();
  }
}
