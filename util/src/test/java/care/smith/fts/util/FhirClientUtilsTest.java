package care.smith.fts.util;

import static care.smith.fts.util.FhirClientUtils.requireOperations;
import static care.smith.fts.util.FhirClientUtils.verifyOperationsExist;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.test.StepVerifier.create;

import java.util.List;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceOperationComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FhirClientUtilsTest {

  private CapabilityStatement capabilityStatement;

  @BeforeEach
  public void setUp() {
    var op1 = new CapabilityStatementRestResourceOperationComponent().setName("operation1");
    var op2 = new CapabilityStatementRestResourceOperationComponent().setName("operation2");
    var rest = new CapabilityStatementRestComponent().setOperation(List.of(op1, op2));

    capabilityStatement = new CapabilityStatement().setRest(List.of(rest));
  }

  @Test
  public void verifyOperationsExistAllOperationsPresent() {
    var operationNames = List.of("operation1", "operation2");
    assertThat(verifyOperationsExist(capabilityStatement, operationNames)).isTrue();
  }

  @Test
  public void verifyOperationsExistWithSomeOperationsMissing() {
    var operationNames = List.of("operation1", "operation3");
    assertThat(verifyOperationsExist(capabilityStatement, operationNames)).isFalse();
  }

  @Test
  public void verifyOperationsExistWithEmptyOperationList() {
    var operationNames = List.<String>of();
    assertThat(verifyOperationsExist(capabilityStatement, operationNames)).isTrue();
  }

  @Test
  public void requireOperationsWithEmptyOperationList() {
    create(requireOperations(capabilityStatement, List.of())).expectNextCount(1).verifyComplete();
  }

  @Test
  public void RequireOperationsWithSomeOperationsMissing() {
    create(requireOperations(capabilityStatement, List.of("operation1", "operation3")))
        .expectError()
        .verify();
  }

  @Test
  public void RequireOperationsAllOperationsPresent() {
    create(requireOperations(capabilityStatement, List.of("operation1", "operation2")))
        .expectNextCount(1)
        .verifyComplete();
  }
}
