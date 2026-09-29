package care.smith.fts.deidentifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class HandlerContextTest {

  @Test
  void namesTheResourceAndTheParentOfTheAncestorChain() {
    Patient patient = new Patient();
    Identifier identifier = new Identifier();
    HandlerContext context = HandlerContext.of(List.of(patient, identifier));

    assertThat(context.resource()).isSameAs(patient);
    assertThat(context.parent()).isSameAs(identifier);
    assertThat(context.patientIdentifier()).isEmpty();
  }

  @Test
  void carriesThePatientIdentifierOfTheCall() {
    HandlerContext context = HandlerContext.of(List.of(new Patient()), "patient-1");

    assertThat(context.patientIdentifier()).contains("patient-1");
  }

  @Test
  void hasNoResourceWithoutAResourceAtTheRootOfTheChain() {
    assertThatThrownBy(() -> HandlerContext.empty().resource())
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> HandlerContext.of(List.of(new Identifier())).resource())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void hasNoParentWithAnEmptyChain() {
    assertThatThrownBy(() -> HandlerContext.empty().parent())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void exposesTheAncestorChain() {
    Patient patient = new Patient();
    Identifier identifier = new Identifier();

    assertThat(HandlerContext.of(List.of(patient, identifier)).ancestors())
        .containsExactly(patient, identifier);
  }
}
