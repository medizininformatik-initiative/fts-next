package care.smith.fts.tca;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@Slf4j
public class GpasProxyE2E extends AbstractTcaE2E {

  @Override
  protected void configureGicsMocks() throws IOException {
    configureGicsMetadataMock();
  }

  @Override
  protected void configureGpasMocks() throws IOException {
    configureGpasMetadataMock();
    configureGpasPseudonymizationMock("patient-id-1", "pseudonym-123");
  }

  @Test
  void proxyReturnsTransportIdsNotRealPseudonyms() {
    var webClient = createTcaWebClient();

    var request = new Parameters();
    request.addParameter().setName("target").setValue(new StringType("domain"));
    request.addParameter().setName("original").setValue(new StringType("patient-id-1"));

    var response =
        webClient
            .post()
            .uri("/api/v2/cd/fp-gpas-proxy/$pseudonymizeAllowCreate")
            .header("Content-Type", "application/fhir+json")
            .header("Accept", "application/fhir+json")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Parameters.class);

    StepVerifier.create(response)
        .assertNext(
            params -> {
              var pseudonymParam =
                  params.getParameter().stream()
                      .filter(p -> "pseudonym".equals(p.getName()))
                      .findFirst()
                      .orElseThrow();

              var originalValue =
                  ((Identifier)
                          pseudonymParam.getPart().stream()
                              .filter(p -> "original".equals(p.getName()))
                              .findFirst()
                              .orElseThrow()
                              .getValue())
                      .getValue();
              assertThat(originalValue).isEqualTo("patient-id-1");

              var tId =
                  ((Identifier)
                          pseudonymParam.getPart().stream()
                              .filter(p -> "pseudonym".equals(p.getName()))
                              .findFirst()
                              .orElseThrow()
                              .getValue())
                      .getValue();
              // Transport ID: 32-char Base64URL, NOT the real pseudonym
              assertThat(tId).hasSize(32).matches("[A-Za-z0-9_-]{32}");
              assertThat(tId).isNotEqualTo("pseudonym-123");
            })
        .verifyComplete();
  }
}
