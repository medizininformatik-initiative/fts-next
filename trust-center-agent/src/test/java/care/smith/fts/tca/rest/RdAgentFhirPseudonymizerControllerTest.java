package care.smith.fts.tca.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import care.smith.fts.tca.services.TransportIdService;
import java.util.Map;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
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

  private static final String SYSTEM = "http://fts.smith.care";

  @Mock private TransportIdService transportIdService;

  private RdAgentFhirPseudonymizerController controller;

  @BeforeEach
  void setUp() {
    controller = new RdAgentFhirPseudonymizerController(transportIdService);
  }

  @Test
  void dePseudonymizeSuccessfullyReturnsOriginalIdentifier() {
    var requestParams = createRequest("test-domain", "tId-123");

    when(transportIdService.fetchMappings(anySet()))
        .thenReturn(Mono.just(Map.of("tId-123", "sId-456")));

    var result = controller.dePseudonymize(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              var params = (Parameters) response.getBody();
              assertThat(params).isNotNull();

              var originalValue = extractOriginalValue(params);
              assertThat(originalValue).isNotNull();
              assertThat(originalValue.getValue()).isEqualTo("sId-456");

              var context = extractOriginalPart(params, "context");
              assertThat(context).isNotNull();
              assertThat(context.getSystem()).isEqualTo(SYSTEM);
              assertThat(context.getValue()).isEqualTo("test-domain");

              var pseudonym = extractOriginalPart(params, "pseudonym");
              assertThat(pseudonym).isNotNull();
              assertThat(pseudonym.getValue()).isEqualTo("tId-123");
            })
        .verifyComplete();
  }

  @Test
  void dePseudonymizeReturnsNotFoundForUnknownTransportId() {
    var requestParams = createRequest("test-domain", "tId-missing");

    when(transportIdService.fetchMappings(anySet())).thenReturn(Mono.just(Map.of()));

    var result = controller.dePseudonymize(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              var outcome = (OperationOutcome) response.getBody();
              assertThat(outcome).isNotNull();
              assertThat(outcome.getIssueFirstRep().getDiagnostics()).contains("not found");
            })
        .verifyComplete();
  }

  @Test
  void dePseudonymizeReturnsBadRequestForMissingContext() {
    var requestParams = new Parameters();
    requestParams
        .addParameter()
        .setName("pseudonym")
        .setValue(new Identifier().setSystem(SYSTEM).setValue("tId-123"));

    var result = controller.dePseudonymize(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              var outcome = (OperationOutcome) response.getBody();
              assertThat(outcome).isNotNull();
              assertThat(outcome.getIssueFirstRep().getDiagnostics())
                  .contains("Missing required parameter 'context'");
            })
        .verifyComplete();
  }

  @Test
  void dePseudonymizeReturnsBadRequestForEmptyContext() {
    var requestParams = new Parameters();
    requestParams
        .addParameter()
        .setName("context")
        .setValue(new Identifier().setSystem(SYSTEM).setValue("   "));
    requestParams
        .addParameter()
        .setName("pseudonym")
        .setValue(new Identifier().setSystem(SYSTEM).setValue("tId-123"));

    var result = controller.dePseudonymize(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              var outcome = (OperationOutcome) response.getBody();
              assertThat(outcome).isNotNull();
              assertThat(outcome.getIssueFirstRep().getDiagnostics()).contains("must not be empty");
            })
        .verifyComplete();
  }

  @Test
  void dePseudonymizeReturnsBadRequestForMissingPseudonym() {
    var requestParams = new Parameters();
    requestParams
        .addParameter()
        .setName("context")
        .setValue(new Identifier().setSystem(SYSTEM).setValue("test-domain"));

    var result = controller.dePseudonymize(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              var outcome = (OperationOutcome) response.getBody();
              assertThat(outcome).isNotNull();
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
            response ->
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
        .verifyComplete();
  }

  private Parameters createRequest(String context, String pseudonym) {
    var params = new Parameters();
    params
        .addParameter()
        .setName("context")
        .setValue(new Identifier().setSystem(SYSTEM).setValue(context));
    params
        .addParameter()
        .setName("pseudonym")
        .setValue(new Identifier().setSystem(SYSTEM).setValue(pseudonym));
    return params;
  }

  /** Extracts the Identifier of part {@code value} of {@code original} (MII IG response shape). */
  private Identifier extractOriginalValue(Parameters params) {
    return extractOriginalPart(params, "value");
  }

  private Identifier extractOriginalPart(Parameters params, String partName) {
    return params.getParameter().stream()
        .filter(p -> "original".equals(p.getName()))
        .findFirst()
        .flatMap(
            p ->
                p.getPart().stream()
                    .filter(part -> partName.equals(part.getName()))
                    .findFirst()
                    .map(part -> (Identifier) part.getValue()))
        .orElse(null);
  }
}
