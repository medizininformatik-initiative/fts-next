package care.smith.fts.tca.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import care.smith.fts.tca.services.TransportIdService;
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
class RdAgentFhirPseudonymizerControllerTest {

  @Mock private TransportIdService transportIdService;

  private RdAgentFhirPseudonymizerController controller;

  @BeforeEach
  void setUp() {
    controller = new RdAgentFhirPseudonymizerController(transportIdService);
  }

  @Test
  void resolvePseudonymsSuccessfullyReturnsSingleEntry() {
    var requestParams = createSingleValueRequest("test-domain", "tId-123", "transfer-id-1");

    when(transportIdService.resolveMappings(eq("transfer-id-1"), anySet()))
        .thenReturn(Mono.just(Map.of("tId-123", "sId-456")));

    var result = controller.resolvePseudonyms(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              var params = response.getBody();
              assertThat(params).isNotNull();

              var namespace = findParameterValue(params, "namespace");
              var originalValue = findParameterValue(params, "originalValue");
              var pseudonymValue = findParameterValue(params, "pseudonymValue");

              assertThat(namespace).isEqualTo("test-domain");
              assertThat(originalValue).isEqualTo("tId-123");
              assertThat(pseudonymValue).isEqualTo("sId-456");
            })
        .verifyComplete();
  }

  @Test
  void resolvePseudonymsSuccessfullyReturnsMultipleEntries() {
    var requestParams = createMultiValueRequest("test-domain", "transfer-id-1", "tId-1", "tId-2");

    when(transportIdService.resolveMappings(eq("transfer-id-1"), anySet()))
        .thenReturn(Mono.just(Map.of("tId-1", "sId-1", "tId-2", "sId-2")));

    var result = controller.resolvePseudonyms(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              var params = response.getBody();
              assertThat(params).isNotNull();
              assertThat(params.getParameter()).hasSize(2);

              var firstPseudonym = params.getParameter().get(0);
              assertThat(firstPseudonym.getName()).isEqualTo("pseudonym");
            })
        .verifyComplete();
  }

  @Test
  void resolvePseudonymsReturnsTidWhenNotFound() {
    var requestParams = createSingleValueRequest("test-domain", "tId-missing", "transfer-id-1");

    when(transportIdService.resolveMappings(eq("transfer-id-1"), anySet()))
        .thenReturn(Mono.just(Map.of()));

    var result = controller.resolvePseudonyms(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              var params = response.getBody();
              assertThat(params).isNotNull();

              var pseudonymValue = findParameterValue(params, "pseudonymValue");
              assertThat(pseudonymValue).isEqualTo("tId-missing");
            })
        .verifyComplete();
  }

  @Test
  void resolvePseudonymsReturnsBadRequestForMissingNamespace() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("originalValue").setValue(new StringType("tId-123"));
    requestParams.addParameter().setName("transferId").setValue(new StringType("transfer-id-1"));

    var result = controller.resolvePseudonyms(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              var params = response.getBody();
              assertThat(params).isNotNull();
              var outcome = (OperationOutcome) params.getParameter().get(0).getResource();
              assertThat(outcome.getIssueFirstRep().getDiagnostics())
                  .contains("Missing required parameter 'namespace'");
            })
        .verifyComplete();
  }

  @Test
  void resolvePseudonymsReturnsBadRequestForEmptyNamespace() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("namespace").setValue(new StringType("   "));
    requestParams.addParameter().setName("originalValue").setValue(new StringType("tId-123"));
    requestParams.addParameter().setName("transferId").setValue(new StringType("transfer-id-1"));

    var result = controller.resolvePseudonyms(requestParams);

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
  void resolvePseudonymsReturnsBadRequestForMissingOriginalValue() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("namespace").setValue(new StringType("test-domain"));
    requestParams.addParameter().setName("transferId").setValue(new StringType("transfer-id-1"));

    var result = controller.resolvePseudonyms(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              var params = response.getBody();
              assertThat(params).isNotNull();
              var outcome = (OperationOutcome) params.getParameter().get(0).getResource();
              assertThat(outcome.getIssueFirstRep().getDiagnostics())
                  .contains("At least one 'originalValue' parameter is required");
            })
        .verifyComplete();
  }

  @Test
  void resolvePseudonymsReturnsBadRequestForMissingTransferId() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("namespace").setValue(new StringType("test-domain"));
    requestParams.addParameter().setName("originalValue").setValue(new StringType("tId-123"));

    var result = controller.resolvePseudonyms(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              var params = response.getBody();
              assertThat(params).isNotNull();
              var outcome = (OperationOutcome) params.getParameter().get(0).getResource();
              assertThat(outcome.getIssueFirstRep().getDiagnostics())
                  .contains("'transferId' is required");
            })
        .verifyComplete();
  }

  @Test
  void resolvePseudonymsReturnsInternalServerErrorOnServiceFailure() {
    var requestParams = createSingleValueRequest("test-domain", "tId-123", "transfer-id-1");

    when(transportIdService.resolveMappings(eq("transfer-id-1"), anySet()))
        .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

    var result = controller.resolvePseudonyms(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            })
        .verifyComplete();
  }

  private Parameters createSingleValueRequest(
      String namespace, String originalValue, String transferId) {
    var params = new Parameters();
    params.addParameter().setName("namespace").setValue(new StringType(namespace));
    params.addParameter().setName("originalValue").setValue(new StringType(originalValue));
    params.addParameter().setName("transferId").setValue(new StringType(transferId));
    return params;
  }

  private Parameters createMultiValueRequest(
      String namespace, String transferId, String... originalValues) {
    var params = new Parameters();
    params.addParameter().setName("namespace").setValue(new StringType(namespace));
    params.addParameter().setName("transferId").setValue(new StringType(transferId));
    for (String value : originalValues) {
      params.addParameter().setName("originalValue").setValue(new StringType(value));
    }
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
