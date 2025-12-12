package care.smith.fts.tca.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.BaseIT;
import care.smith.fts.test.TestWebClientFactory;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Integration tests for TransportIdService.
 *
 * <p>Tests transport ID generation, Redis storage, and resolution functionality.
 */
@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
class TransportIdServiceIT extends BaseIT {

  @Autowired private TransportIdService transportIdService;
  @Autowired private RedissonClient redisClient;

  @BeforeEach
  void setUp() {
    redisClient.getKeys().deleteByPattern("transport-mapping:*");
    redisClient.getKeys().deleteByPattern("tid:*");
  }

  @Test
  void generateId_shouldReturnBase64UrlEncodedString() {
    var id = transportIdService.generateId();

    assertThat(id)
        .isNotNull()
        .hasSize(32)
        .matches(s -> s.matches("^[A-Za-z0-9_-]+$"), "should be Base64URL encoded");
  }

  @Test
  void generateId_shouldBeUnique() {
    var id1 = transportIdService.generateId();
    var id2 = transportIdService.generateId();
    var id3 = transportIdService.generateId();

    assertThat(id1).isNotEqualTo(id2).isNotEqualTo(id3);
    assertThat(id2).isNotEqualTo(id3);
  }

  @Test
  void storeAndFetchAllMappings_shouldWork() {
    var transferId = "test-transfer-123";
    var allData = Map.of("tId-1", "sId-1", "tId-2", "sId-2", "dateShiftMillis", "1000");

    var storeMono = transportIdService.storeAllMappings(transferId, allData, Duration.ofMinutes(5));
    create(storeMono).verifyComplete();

    var fetchMono = transportIdService.fetchAllMappings(transferId);

    create(fetchMono)
        .assertNext(
            mappings -> {
              assertThat(mappings).containsExactlyInAnyOrderEntriesOf(allData);
            })
        .verifyComplete();
  }

  @Test
  void fetchAllMappings_withUnknownTransferId_shouldReturnEmpty() {
    var fetchMono = transportIdService.fetchAllMappings("non-existent-transfer");

    create(fetchMono).assertNext(mappings -> assertThat(mappings).isEmpty()).verifyComplete();
  }

  @Test
  void storeAndFetchMapping_shouldWork() {
    var tid = transportIdService.generateId();
    var sid = "sId-12345";

    var storeMono = transportIdService.storeMapping(tid, sid, Duration.ofMinutes(5));
    create(storeMono).verifyComplete();

    var fetchMono = transportIdService.fetchMapping(tid);
    create(fetchMono)
        .assertNext(retrieved -> assertThat(retrieved).isEqualTo(sid))
        .verifyComplete();
  }

  @Test
  void fetchMapping_withUnknownTid_shouldReturnEmpty() {
    var fetchMono = transportIdService.fetchMapping("non-existent-tid");

    create(fetchMono).verifyComplete();
  }

  @Test
  void storeAndFetchMappings_batch_shouldWork() {
    var mappings =
        Map.of(
            transportIdService.generateId(), "sId-1",
            transportIdService.generateId(), "sId-2",
            transportIdService.generateId(), "sId-3");

    var storeMono = transportIdService.storeMappings(mappings, Duration.ofMinutes(5));
    create(storeMono).verifyComplete();

    var fetchMono = transportIdService.fetchMappings(mappings.keySet());
    create(fetchMono)
        .assertNext(
            retrieved -> {
              assertThat(retrieved).containsExactlyInAnyOrderEntriesOf(mappings);
            })
        .verifyComplete();
  }

  @Test
  void fetchMappings_partialResults_shouldOnlyReturnFound() {
    var tid1 = transportIdService.generateId();
    var tid2 = transportIdService.generateId();
    var sid1 = "sId-exists";

    var storeMono = transportIdService.storeMapping(tid1, sid1, Duration.ofMinutes(5));
    create(storeMono).verifyComplete();

    var fetchMono = transportIdService.fetchMappings(Set.of(tid1, tid2));
    create(fetchMono)
        .assertNext(
            retrieved -> {
              assertThat(retrieved).hasSize(1);
              assertThat(retrieved).containsEntry(tid1, sid1);
              assertThat(retrieved).doesNotContainKey(tid2);
            })
        .verifyComplete();
  }
}
