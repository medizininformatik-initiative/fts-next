package care.smith.fts.util;

import static care.smith.fts.util.FhirClientUtils.verifyOperationsExist;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceOperationComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FhirClientUtilsTest {

  private CapabilityStatement capabilityStatement;

  @BeforeEach
  public void setUp() {
    capabilityStatement = new CapabilityStatement();

    var restComponent = new CapabilityStatementRestComponent();
    var resourceComponent = new CapabilityStatementRestResourceComponent();

    var operation1 = new CapabilityStatementRestResourceOperationComponent();
    operation1.setName("operation1");

    var operation2 = new CapabilityStatementRestResourceOperationComponent();
    operation2.setName("operation2");

    resourceComponent.setOperation(Arrays.asList(operation1, operation2));
    restComponent.setResource(Collections.singletonList(resourceComponent));
    capabilityStatement.setRest(Collections.singletonList(restComponent));
  }

  @Test
  public void testAllOperationsPresent() {
    var operationNames = List.of("operation1", "operation2");
    assertThat(verifyOperationsExist(capabilityStatement, operationNames)).isTrue();
  }

  @Test
  public void testSomeOperationsMissing() {
    var operationNames = List.of("operation1", "operation3");
    assertThat(verifyOperationsExist(capabilityStatement, operationNames)).isFalse();
  }

  @Test
  public void testEmptyOperationList() {
    var operationNames = List.<String>of();
    assertThat(verifyOperationsExist(capabilityStatement, operationNames)).isTrue();
  }
}
