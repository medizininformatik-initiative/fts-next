package care.smith.fts.tca.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
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
  void dePseudonymizeSuccessfullyReturnsOriginal() {
    var requestParams = createRequest("test-domain", "tId-123");

    when(transportIdService.fetchMappings(anySet()))
        .thenReturn(Mono.just(Map.of("tId-123", "sId-456")));

    var result = controller.dePseudonymize(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              var params = response.getBody();
              assertThat(params).isNotNull();

              var originalValue = extractOriginalValue(params);
              assertThat(originalValue).isEqualTo("sId-456");
            })
        .verifyComplete();
  }

  @Test
  void dePseudonymizeReturnsTidWhenNotFound() {
    var requestParams = createRequest("test-domain", "tId-missing");

    when(transportIdService.fetchMappings(anySet())).thenReturn(Mono.just(Map.of()));

    var result = controller.dePseudonymize(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              var params = response.getBody();
              assertThat(params).isNotNull();

              var originalValue = extractOriginalValue(params);
              assertThat(originalValue).isEqualTo("tId-missing");
            })
        .verifyComplete();
  }

  @Test
  void dePseudonymizeReturnsBadRequestForMissingTarget() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("pseudonym").setValue(new StringType("tId-123"));

    var result = controller.dePseudonymize(requestParams);

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
  void dePseudonymizeReturnsBadRequestForEmptyTarget() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("target").setValue(new StringType("   "));
    requestParams.addParameter().setName("pseudonym").setValue(new StringType("tId-123"));

    var result = controller.dePseudonymize(requestParams);

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
  void dePseudonymizeReturnsBadRequestForMissingPseudonym() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("target").setValue(new StringType("test-domain"));

    var result = controller.dePseudonymize(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              var params = response.getBody();
              assertThat(params).isNotNull();
              var outcome = (OperationOutcome) params.getParameter().get(0).getResource();
              assertThat(outcome.getIssueFirstRep().getDiagnostics())
                  .contains("Missing required parameter 'pseudonym'");
            })
        .verifyComplete();
  }

  @Test
  void dePseudonymizeReturnsInternalServerErrorOnServiceFailure() {
    var requestParams = createRequest("test-domain", "tId-123");

    when(transportIdService.fetchMappings(anySet()))
        .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

    var result = controller.dePseudonymize(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            })
        .verifyComplete();
  }

  private Parameters createRequest(String target, String pseudonym) {
    var params = new Parameters();
    params.addParameter().setName("target").setValue(new StringType(target));
    params.addParameter().setName("pseudonym").setValue(new StringType(pseudonym));
    return params;
  }

  /** Extracts the value from MII $de-pseudonymize response: original -> part[value] */
  private String extractOriginalValue(Parameters params) {
    return params.getParameter().stream()
        .filter(p -> "original".equals(p.getName()))
        .findFirst()
        .flatMap(
            p ->
                p.getPart().stream()
                    .filter(part -> "value".equals(part.getName()))
                    .findFirst()
                    .map(part -> part.getValue().primitiveValue()))
        .orElse(null);
  }
}
