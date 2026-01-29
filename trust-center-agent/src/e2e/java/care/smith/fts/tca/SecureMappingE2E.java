package care.smith.fts.tca;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.util.tca.SecureMappingResponse;
import care.smith.fts.util.tca.TcaDomains;
import care.smith.fts.util.tca.TransportMappingRequest;
import care.smith.fts.util.tca.TransportMappingResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * E2E test for the secure mapping endpoint which retrieves pseudonym mappings by transfer ID from
 * Redis storage.
 */
@Slf4j
public class SecureMappingE2E extends AbstractTcaE2E {

  @Override
  protected void configureGicsMocks() throws IOException {
    configureGicsMetadataMock();
  }

  @Override
  protected void configureGpasMocks() throws IOException {
    configureStandardGpasMocks();
  }

  @Test
  void testSecureMappingRetrieval() {
    var webClient = createTcaWebClient();

    // First, create a transport mapping which will be stored in Redis
    // Now that gPAS mocking is fixed, we can test the full flow with real resourceIds
    var tcaDomains = new TcaDomains("domain", "domain", "domain");
    var transportRequest =
        new TransportMappingRequest(
            "patient-id-1",
            "http://fts.smith.care",
            Set.of(
                "patient-id-1.Patient:patient-id-1",
                "patient-id-1.identifier.http://fts.smith.care:patient-identifier-1"),
            Map.of(),
            tcaDomains,
            Duration.ofDays(14),
            DateShiftPreserve.NONE);

    log.info("Creating transport mapping");

    // Create the mapping first
    var createResponse =
        webClient
            .post()
            .uri("/api/v2/cd/transport-mapping")
            .header("Content-Type", "application/json")
            .bodyValue(transportRequest)
            .retrieve()
            .bodyToMono(TransportMappingResponse.class);

    // Store the transferId from the response for later retrieval
    var transferId =
        createResponse
            .map(TransportMappingResponse::transferId)
            .doOnNext(id -> log.info("Transport mapping created with transfer ID: {}", id))
            .block();

    assertThat(transferId).isNotNull();

    // Now retrieve the secure mapping using the transfer ID
    log.info("Retrieving secure mapping for transfer ID: {}", transferId);

    var secureResponse =
        webClient
            .post()
            .uri("/api/v2/rd/secure-mapping")
            .header("Content-Type", "application/json")
            .bodyValue(transferId)
            .retrieve()
            .bodyToMono(SecureMappingResponse.class);

    StepVerifier.create(secureResponse)
        .assertNext(
            secureMapping -> {
              log.info("Retrieved secure mapping: {}", secureMapping);

              assertThat(secureMapping).isNotNull();
              Map<String, String> mapping = secureMapping.tidPidMap();
              assertThat(mapping).isNotNull();
              assertThat(mapping).isNotEmpty();

              // Verify the secure mapping contains the reverse mappings (pseudonym -> original)
              mapping.forEach(
                  (key, value) -> {
                    assertThat(key).isNotNull().isNotEmpty();
                    assertThat(value).isNotNull().isNotEmpty();
                    log.info("Secure mapping entry: {} -> {}", key, value);
                  });

              // Verify date shift map is present
              assertThat(secureMapping.dateShiftMap()).isNotNull();

              log.info("Successfully retrieved secure mapping with {} entries", mapping.size());
            })
        .verifyComplete();
  }
}
