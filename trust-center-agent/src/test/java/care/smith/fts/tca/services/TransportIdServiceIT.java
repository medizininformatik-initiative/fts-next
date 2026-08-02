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
    // Clean up any existing test data
    redisClient.getKeys().deleteByPattern("transport-mapping:*");
  }

  @Test
  void generateTransportId_shouldReturnBase64UrlEncodedString() {
    var tId = transportIdService.generateTransportId();

    assertThat(tId)
        .isNotNull()
        .hasSize(32)
        .matches(s -> s.matches("^[A-Za-z0-9_-]+$"), "should be Base64URL encoded");
  }

  @Test
  void generateTransportId_shouldBeUnique() {
    var id1 = transportIdService.generateTransportId();
    var id2 = transportIdService.generateTransportId();
    var id3 = transportIdService.generateTransportId();

    assertThat(id1).isNotEqualTo(id2).isNotEqualTo(id3);
    assertThat(id2).isNotEqualTo(id3);
  }

  @Test
  void storeAndRetrieveMapping_shouldWork() {
    var transferId = "test-transfer-123";
    var tId = "test-transport-id-abc";
    var sId = "secure-pseudonym-xyz";
    var domain = "test-domain";

    // Store mapping
    var storeMono =
        transportIdService.storeMapping(transferId, tId, sId, domain, Duration.ofMinutes(5));

    create(storeMono).expectNext(tId).verifyComplete();

    // Retrieve mapping
    var retrieveMono = transportIdService.resolveMappings(transferId, Set.of(tId));

    create(retrieveMono)
        .assertNext(
            mappings -> {
              assertThat(mappings).containsEntry(tId, sId);
            })
        .verifyComplete();
  }

  @Test
  void storeMultipleMappings_shouldWork() {
    var transferId = "test-transfer-multi";
    var mappings =
        Map.of(
            "tId-1", "sId-1",
            "tId-2", "sId-2",
            "tId-3", "sId-3");
    var domain = "test-domain";

    // Store all mappings
    var storeMono =
        transportIdService.storeMappings(transferId, mappings, domain, Duration.ofMinutes(5));

    create(storeMono)
        .assertNext(
            stored -> {
              assertThat(stored).containsExactlyInAnyOrderEntriesOf(mappings);
            })
        .verifyComplete();

    // Retrieve all mappings
    var retrieveMono = transportIdService.resolveMappings(transferId, mappings.keySet());

    create(retrieveMono)
        .assertNext(
            resolved -> {
              assertThat(resolved).containsExactlyInAnyOrderEntriesOf(mappings);
            })
        .verifyComplete();
  }

  @Test
  void resolveMappings_withUnknownTransferId_shouldReturnEmpty() {
    var retrieveMono =
        transportIdService.resolveMappings("non-existent-transfer", Set.of("some-tid"));

    create(retrieveMono).assertNext(mappings -> assertThat(mappings).isEmpty()).verifyComplete();
  }

  @Test
  void resolveMappings_withPartialMatch_shouldReturnOnlyKnownMappings() {
    var transferId = "test-transfer-partial";
    var tId = "known-tid";
    var sId = "known-sid";
    var domain = "test-domain";

    // Store one mapping
    transportIdService.storeMapping(transferId, tId, sId, domain, Duration.ofMinutes(5)).block();

    // Try to resolve known and unknown tIDs
    var retrieveMono = transportIdService.resolveMappings(transferId, Set.of(tId, "unknown-tid"));

    create(retrieveMono)
        .assertNext(
            mappings -> {
              assertThat(mappings).hasSize(1).containsEntry(tId, sId);
            })
        .verifyComplete();
  }

  @Test
  void storeDateShiftValue_shouldPersistAndRetrieve() {
    var transferId = "test-transfer-dateshift";
    var dateShiftMillis = 86400000L; // 1 day

    // Store date shift value
    var storeMono =
        transportIdService.storeDateShiftValue(transferId, dateShiftMillis, Duration.ofMinutes(5));

    create(storeMono).expectNext(dateShiftMillis).verifyComplete();

    // Retrieve date shift value
    var retrieveMono = transportIdService.getDateShiftValue(transferId);

    create(retrieveMono)
        .assertNext(
            value -> {
              assertThat(value).isEqualTo(dateShiftMillis);
            })
        .verifyComplete();
  }
}
