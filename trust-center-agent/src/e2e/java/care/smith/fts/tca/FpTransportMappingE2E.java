package care.smith.fts.tca;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.tca.TransportMappingResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Parameters;
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

    // Step 1: Create tID->sID in Redis via MII $pseudonymize endpoint
    var system = "http://fts.smith.care";
    var pseudonymizeRequest = new Parameters();
    pseudonymizeRequest
        .addParameter()
        .setName("context")
        .setValue(new Identifier().setSystem(system).setValue("domain"));
    pseudonymizeRequest
        .addParameter()
        .setName("original")
        .setValue(new Identifier().setSystem(system).setValue("patient-id-1"));

    var pseudonymizeResponse =
        webClient
            .post()
            .uri("/api/v2/cd/fhir/$pseudonymize")
            .header("Content-Type", "application/fhir+json")
            .header("Accept", "application/fhir+json")
            .bodyValue(pseudonymizeRequest)
            .retrieve()
            .bodyToMono(Parameters.class)
            .block();

    var tId =
        pseudonymizeResponse.getParameter().stream()
            .filter(p -> "pseudonym".equals(p.getName()))
            .map(p -> ((Identifier) p.getValue()).getValue())
            .findFirst()
            .orElseThrow();

    log.info("Got transport ID from $pseudonymize: {}", tId);

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
