package care.smith.fts.tca.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import care.smith.fts.tca.adapters.PseudonymBackendAdapter;
import care.smith.fts.tca.services.BackendAdapterFactory;
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
  @Mock private BackendAdapterFactory adapterFactory;
  @Mock private PseudonymBackendAdapter backendAdapter;

  private CdAgentFhirPseudonymizerController controller;

  @BeforeEach
  void setUp() {
    when(adapterFactory.createAdapter()).thenReturn(backendAdapter);
    controller = new CdAgentFhirPseudonymizerController(transportIdService, adapterFactory);
  }

  @Test
  void createPseudonymSuccessfullyReturnsSingleEntry() {
    var requestParams = createSingleValueRequest("test-domain", "patient-123");
    var ttl = Duration.ofMinutes(10);

    when(transportIdService.generateTransferId()).thenReturn("transfer-id-1");
    when(transportIdService.getDefaultTtl()).thenReturn(ttl);
    when(transportIdService.generateTransportId()).thenReturn("tId-abc123");
    when(transportIdService.storeMapping(
            eq("transfer-id-1"), eq("tId-abc123"), eq("sId-456"), eq("test-domain"), eq(ttl)))
        .thenReturn(Mono.just("tId-abc123"));
    when(backendAdapter.fetchOrCreatePseudonyms(eq("test-domain"), anySet()))
        .thenReturn(Mono.just(Map.of("patient-123", "sId-456")));

    var result = controller.createPseudonym(requestParams);

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
              assertThat(originalValue).isEqualTo("patient-123");
              assertThat(pseudonymValue).isEqualTo("tId-abc123");
            })
        .verifyComplete();
  }

  @Test
  void createPseudonymSuccessfullyReturnsMultipleEntries() {
    var requestParams = createMultiValueRequest("test-domain", "patient-1", "patient-2");
    var ttl = Duration.ofMinutes(10);

    when(transportIdService.generateTransferId()).thenReturn("transfer-id-1");
    when(transportIdService.getDefaultTtl()).thenReturn(ttl);
    when(transportIdService.generateTransportId()).thenReturn("tId-1", "tId-2");
    when(transportIdService.storeMapping(anyString(), anyString(), anyString(), anyString(), any()))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(1)));
    when(backendAdapter.fetchOrCreatePseudonyms(eq("test-domain"), anySet()))
        .thenReturn(Mono.just(Map.of("patient-1", "sId-1", "patient-2", "sId-2")));

    var result = controller.createPseudonym(requestParams);

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
  void createPseudonymReturnsBadRequestForMissingNamespace() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("originalValue").setValue(new StringType("patient-123"));

    var result = controller.createPseudonym(requestParams);

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
  void createPseudonymReturnsBadRequestForEmptyNamespace() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("namespace").setValue(new StringType("   "));
    requestParams.addParameter().setName("originalValue").setValue(new StringType("patient-123"));

    var result = controller.createPseudonym(requestParams);

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
  void createPseudonymReturnsBadRequestForMissingOriginalValue() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("namespace").setValue(new StringType("test-domain"));

    var result = controller.createPseudonym(requestParams);

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
  void createPseudonymReturnsInternalServerErrorOnBackendFailure() {
    var requestParams = createSingleValueRequest("test-domain", "patient-123");

    when(transportIdService.generateTransferId()).thenReturn("transfer-id-1");
    when(backendAdapter.fetchOrCreatePseudonyms(eq("test-domain"), anySet()))
        .thenReturn(Mono.error(new RuntimeException("Backend connection failed")));

    var result = controller.createPseudonym(requestParams);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            })
        .verifyComplete();
  }

  private Parameters createSingleValueRequest(String namespace, String originalValue) {
    var params = new Parameters();
    params.addParameter().setName("namespace").setValue(new StringType(namespace));
    params.addParameter().setName("originalValue").setValue(new StringType(originalValue));
    return params;
  }

  private Parameters createMultiValueRequest(String namespace, String... originalValues) {
    var params = new Parameters();
    params.addParameter().setName("namespace").setValue(new StringType(namespace));
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
