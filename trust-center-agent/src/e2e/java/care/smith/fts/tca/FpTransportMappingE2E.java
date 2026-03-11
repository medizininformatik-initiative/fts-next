package care.smith.fts.tca;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.tca.TransportMappingResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@Slf4j
public class FpTransportMappingE2E extends AbstractTcaE2E {

  @Override
  protected void configureGicsMocks() throws IOException {
    configureGicsMetadataMock();
  }

  @Override
  protected void configureGpasMocks() throws IOException {
    configureGpasMetadataMock();
    configureGpasPseudonymizationMock("patient-id-1", "pseudonym-123");
    // PT336H = Duration.ofDays(14).toString()
    configureGpasPseudonymizationMock("PT336H_patient-id-1", "dateshift-seed-456");
  }

  @Test
  void testFpTransportMappingConsolidation() {
    var webClient = createTcaWebClient();

    // Step 1: Create tID->sID in Redis via gPAS proxy
    var proxyRequest = new Parameters();
    proxyRequest.addParameter().setName("target").setValue(new StringType("domain"));
    proxyRequest.addParameter().setName("original").setValue(new StringType("patient-id-1"));

    var proxyResponse =
        webClient
            .post()
            .uri("/api/v2/cd/fp-gpas-proxy/$pseudonymizeAllowCreate")
            .header("Content-Type", "application/fhir+json")
            .header("Accept", "application/fhir+json")
            .bodyValue(proxyRequest)
            .retrieve()
            .bodyToMono(Parameters.class)
            .block();

    var tId =
        proxyResponse.getParameter().stream()
            .filter(p -> "pseudonym".equals(p.getName()))
            .flatMap(p -> p.getPart().stream())
            .filter(p -> "pseudonym".equals(p.getName()))
            .map(p -> ((Identifier) p.getValue()).getValue())
            .findFirst()
            .orElseThrow();

    log.info("Got transport ID from proxy: {}", tId);

    // Step 2: Consolidate via FP transport mapping endpoint
    var fpRequest =
        Map.of(
            "patientIdentifier", "patient-id-1",
            "transportIds", Set.of(tId),
            "dateMappings", Map.of("dateTid-1", "2020-06-15"),
            "dateShiftDomain", "domain",
            "maxDateShift", "PT336H",
            "dateShiftPreserve", "NONE");

    var response =
        webClient
            .post()
            .uri("/api/v2/cd/fhir-pseudonymizer/transport-mapping")
            .header("Content-Type", "application/json")
            .bodyValue(fpRequest)
            .retrieve()
            .bodyToMono(TransportMappingResponse.class);

    StepVerifier.create(response)
        .assertNext(
            tmr -> {
              log.info("Received transfer ID: {}", tmr.transferId());
              assertThat(tmr.transferId()).isNotNull().hasSize(32);
            })
        .verifyComplete();
  }
}
