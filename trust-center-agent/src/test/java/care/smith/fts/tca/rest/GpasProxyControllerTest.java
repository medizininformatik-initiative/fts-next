package care.smith.fts.tca.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import care.smith.fts.tca.deidentification.GpasClient;
import care.smith.fts.tca.services.TransportIdService;
import java.time.Duration;
import java.util.Map;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GpasProxyControllerTest {

  @Mock private TransportIdService transportIdService;
  @Mock private GpasClient gpasClient;

  private GpasProxyController controller;

  @BeforeEach
  void setUp() {
    controller = new GpasProxyController(transportIdService, gpasClient);
  }

  @Test
  void singleOriginalReturnsSinglePseudonymEntry() {
    var requestParams = createGpasRequest("MII", "patient-123");
    var ttl = Duration.ofMinutes(10);

    when(transportIdService.generateId()).thenReturn("tId-abc123");
    when(transportIdService.getDefaultTtl()).thenReturn(ttl);
    when(transportIdService.storeMapping(eq("tId-abc123"), eq("sId-456"), eq(ttl)))
        .thenReturn(Mono.empty());
    when(gpasClient.fetchOrCreatePseudonyms(eq("MII"), anySet()))
        .thenReturn(Mono.just(Map.of("patient-123", "sId-456")));

    StepVerifier.create(controller.pseudonymizeAllowCreate(requestParams))
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              var params = response.getBody();
              assertThat(params).isNotNull();
              assertThat(params.getParameter()).hasSize(1);

              var pseudonymParam = params.getParameter().get(0);
              assertThat(pseudonymParam.getName()).isEqualTo("pseudonym");

              var originalPart = findPart(pseudonymParam, "original");
              assertThat(originalPart).isNotNull();
              assertThat(((Identifier) originalPart.getValue()).getValue())
                  .isEqualTo("patient-123");

              var pseudonymPart = findPart(pseudonymParam, "pseudonym");
              assertThat(pseudonymPart).isNotNull();
              assertThat(((Identifier) pseudonymPart.getValue()).getValue())
                  .isEqualTo("tId-abc123");
            })
        .verifyComplete();
  }

  @Test
  void multipleOriginalsReturnsBatchResponse() {
    var requestParams = createGpasRequest("MII", "patient-1", "patient-2");
    var ttl = Duration.ofMinutes(10);

    when(transportIdService.generateId()).thenReturn("tId-1", "tId-2");
    when(transportIdService.getDefaultTtl()).thenReturn(ttl);
    when(transportIdService.storeMapping(anyString(), anyString(), any(Duration.class)))
        .thenReturn(Mono.empty());
    when(gpasClient.fetchOrCreatePseudonyms(eq("MII"), anySet()))
        .thenReturn(Mono.just(Map.of("patient-1", "sId-1", "patient-2", "sId-2")));

    StepVerifier.create(controller.pseudonymizeAllowCreate(requestParams))
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              var params = response.getBody();
              assertThat(params).isNotNull();
              assertThat(params.getParameter()).hasSize(2);

              params
                  .getParameter()
                  .forEach(p -> assertThat(p.getName()).isEqualTo("pseudonym"));
            })
        .verifyComplete();
  }

  @Test
  void missingTargetReturnsBadRequest() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("original").setValue(new StringType("patient-123"));

    StepVerifier.create(controller.pseudonymizeAllowCreate(requestParams))
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              var params = response.getBody();
              assertThat(params).isNotNull();
              var outcome = (OperationOutcome) params.getParameter().get(0).getResource();
              assertThat(outcome.getIssueFirstRep().getDiagnostics())
                  .contains("Missing required parameter 'target'");
            })
        .verifyComplete();
  }

  @Test
  void missingOriginalReturnsBadRequest() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("target").setValue(new StringType("MII"));

    StepVerifier.create(controller.pseudonymizeAllowCreate(requestParams))
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              var params = response.getBody();
              assertThat(params).isNotNull();
              var outcome = (OperationOutcome) params.getParameter().get(0).getResource();
              assertThat(outcome.getIssueFirstRep().getDiagnostics())
                  .contains("At least one 'original' parameter is required");
            })
        .verifyComplete();
  }

  @Test
  void gpasFailureReturnsInternalServerError() {
    var requestParams = createGpasRequest("MII", "patient-123");

    when(transportIdService.getDefaultTtl()).thenReturn(Duration.ofMinutes(10));
    when(gpasClient.fetchOrCreatePseudonyms(eq("MII"), anySet()))
        .thenReturn(Mono.error(new RuntimeException("gPAS connection failed")));

    StepVerifier.create(controller.pseudonymizeAllowCreate(requestParams))
        .assertNext(
            response ->
                assertThat(response.getStatusCode())
                    .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
        .verifyComplete();
  }

  private Parameters createGpasRequest(String target, String... originals) {
    var params = new Parameters();
    params.addParameter().setName("target").setValue(new StringType(target));
    for (String original : originals) {
      params.addParameter().setName("original").setValue(new StringType(original));
    }
    return params;
  }

  private Parameters.ParametersParameterComponent findPart(
      Parameters.ParametersParameterComponent param, String name) {
    return param.getPart().stream()
        .filter(p -> name.equals(p.getName()))
        .findFirst()
        .orElse(null);
  }
}
