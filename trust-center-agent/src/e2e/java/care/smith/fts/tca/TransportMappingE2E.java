package care.smith.fts.tca;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.util.tca.TcaDomains;
import care.smith.fts.util.tca.TransportMappingRequest;
import care.smith.fts.util.tca.TransportMappingResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * E2E test for the transport mapping endpoint which generates pseudonyms and date shifts for
 * patient identifiers.
 */
@Slf4j
public class TransportMappingE2E extends AbstractTcaE2E {

  @Override
  protected void configureGicsMocks() throws IOException {
    configureGicsMetadataMock();
  }

  @Override
  protected void configureGpasMocks() throws IOException {
    configureStandardGpasMocks();
  }

  @Test
  void testTransportMapping() {
    var webClient = createTcaWebClient();

    // Create a transport mapping request with CDA-generated transport IDs
    var tcaDomains = new TcaDomains("domain", "domain", "domain");
    var idMappings =
        Map.of(
            "patient-id-1.Patient:patient-id-1", "tid-patient-1",
            "patient-id-1.identifier.http://fts.smith.care:patient-identifier-1",
                "tid-identifier-1");
    var request =
        new TransportMappingRequest(
            "patient-id-1",
            "http://fts.smith.care",
            idMappings,
            Map.of(),
            tcaDomains,
            Duration.ofDays(14),
            DateShiftPreserve.NONE);

    log.info("Sending transport mapping request: {}", request);

    // Send request to TCA
    var response =
        webClient
            .post()
            .uri("/api/v2/cd/transport-mapping")
            .header("Content-Type", "application/json")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(TransportMappingResponse.class);

    // Verify the response
    StepVerifier.create(response)
        .assertNext(
            transportMapping -> {
              log.info("Received transport mapping response: {}", transportMapping);

              // Verify transfer ID is generated
              assertThat(transportMapping.transferId()).isNotNull();
            })
        .verifyComplete();
  }
}
