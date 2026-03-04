package care.smith.fts.tca.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import care.smith.fts.tca.deidentification.GpasClient;
import care.smith.fts.tca.services.TransportIdService;
import java.time.Duration;
import java.util.Map;
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
class CdAgentFhirPseudonymizerControllerTest {

  @Mock private TransportIdService transportIdService;
  @Mock private GpasClient gpasClient;

  private CdAgentFhirPseudonymizerController controller;

  @BeforeEach
  void setUp() {
    controller = new CdAgentFhirPseudonymizerController(transportIdService, gpasClient);
  }

  @Test
  void pseudonymizeSuccessfullyReturnsPseudonym() {
    var requestParams = createRequest("test-domain", "patient-123");
    var ttl = Duration.ofMinutes(10);

    when(transportIdService.generateId()).thenReturn("tId-abc123");
    when(transportIdService.getDefaultTtl()).thenReturn(ttl);
    when(transportIdService.storeMapping(eq("tId-abc123"), eq("sId-456"), eq(ttl)))
        .thenReturn(Mono.empty());
    when(gpasClient.fetchOrCreatePseudonyms(eq("test-domain"), anySet()))
        .thenReturn(Mono.just(Map.of("patient-123", "sId-456")));

    var result = controller.pseudonymize(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              var params = response.getBody();
              assertThat(params).isNotNull();

              var pseudonym = findParameterValue(params, "pseudonym");
              assertThat(pseudonym).isEqualTo("tId-abc123");
            })
        .verifyComplete();
  }

  @Test
  void pseudonymizeReturnsBadRequestForMissingTarget() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("original").setValue(new StringType("patient-123"));

    var result = controller.pseudonymize(requestParams);

    StepVerifier.create(result)
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
  void pseudonymizeReturnsBadRequestForEmptyTarget() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("target").setValue(new StringType("   "));
    requestParams.addParameter().setName("original").setValue(new StringType("patient-123"));

    var result = controller.pseudonymize(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              var params = response.getBody();
              assertThat(params).isNotNull();
              var outcome = (OperationOutcome) params.getParameter().get(0).getResource();
              assertThat(outcome.getIssueFirstRep().getDiagnostics()).contains("must not be empty");
            })
        .verifyComplete();
  }

  @Test
  void pseudonymizeReturnsBadRequestForMissingOriginal() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("target").setValue(new StringType("test-domain"));

    var result = controller.pseudonymize(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              var params = response.getBody();
              assertThat(params).isNotNull();
              var outcome = (OperationOutcome) params.getParameter().get(0).getResource();
              assertThat(outcome.getIssueFirstRep().getDiagnostics())
                  .contains("Missing required parameter 'original'");
            })
        .verifyComplete();
  }

  @Test
  void pseudonymizeReturnsInternalServerErrorOnBackendFailure() {
    var requestParams = createRequest("test-domain", "patient-123");

    when(gpasClient.fetchOrCreatePseudonyms(eq("test-domain"), anySet()))
        .thenReturn(Mono.error(new RuntimeException("Backend connection failed")));

    var result = controller.pseudonymize(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            })
        .verifyComplete();
  }

  private Parameters createRequest(String target, String original) {
    var params = new Parameters();
    params.addParameter().setName("target").setValue(new StringType(target));
    params.addParameter().setName("original").setValue(new StringType(original));
    return params;
  }

  private String findParameterValue(Parameters params, String name) {
    return params.getParameter().stream()
        .filter(p -> name.equals(p.getName()))
        .findFirst()
        .map(p -> p.getValue().primitiveValue())
        .orElse(null);
  }
}
