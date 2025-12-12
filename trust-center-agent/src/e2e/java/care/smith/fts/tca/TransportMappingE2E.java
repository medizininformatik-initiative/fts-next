package care.smith.fts.tca;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.DateShiftPreserve;
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

    // Create a transport mapping request with patient identifiers
    var tcaDomains = new TcaDomains("domain", "domain", "domain");
    var request =
        new TransportMappingRequest(
            "patient-id-1",
            "http://fts.smith.care",
            Set.of(
                "patient-id-1.Patient:patient-id-1",
                "patient-id-1.identifier.http://fts.smith.care:patient-identifier-1"),
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

              // Verify transport mapping contains pseudonyms
              Map<String, String> mapping = transportMapping.transportMapping();
              assertThat(mapping).isNotNull();
              assertThat(mapping).isNotEmpty();
              assertThat(mapping)
                  .containsKeys(
                      "patient-id-1.Patient:patient-id-1",
                      "patient-id-1.identifier.http://fts.smith.care:patient-identifier-1");

              // Verify each identifier has a pseudonym
              mapping
                  .values()
                  .forEach(
                      pseudonym -> {
                        assertThat(pseudonym).isNotNull();
                        assertThat(pseudonym).isNotEmpty();
                      });

              // Verify date shift value exists and is non-zero
              assertThat(transportMapping.dateShiftValue()).isNotNull();
              assertThat(transportMapping.dateShiftValue()).isNotEqualTo(Duration.ZERO);
              log.info("Date shift value: {}", transportMapping.dateShiftValue());
            })
        .verifyComplete();
  }
}
