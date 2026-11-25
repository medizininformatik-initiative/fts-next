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
    HandlerContext context = HandlerContext.of(List.of(patient, identifier), "p");

    assertThat(context.resource()).isSameAs(patient);
    assertThat(context.parent()).isSameAs(identifier);
  }

  @Test
  void hasNoResourceWithoutAResourceAtTheRootOfTheChain() {
    assertThatThrownBy(() -> HandlerContext.of(List.of(), "p").resource())
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> HandlerContext.of(List.of(new Identifier()), "p").resource())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void hasNoParentWithAnEmptyChain() {
    assertThatThrownBy(() -> HandlerContext.of(List.of(), "p").parent())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void exposesTheAncestorChain() {
    Patient patient = new Patient();
    Identifier identifier = new Identifier();

    assertThat(HandlerContext.of(List.of(patient, identifier), "p").ancestors())
        .containsExactly(patient, identifier);
  }

  @Test
  void carriesThePatientIdentifierTheCallerNamed() {
    var patient = new Patient();

    var context = HandlerContext.of(List.of(patient), "p1");

    assertThat(context.patientIdentifier()).isEqualTo("p1");
    assertThat(context.ancestors()).containsExactly(patient);
  }

  @Test
  void keepsThePatientIdentifierOneLevelDeeper() {
    var child = HandlerContext.of(List.of(new Patient()), "p1").child(new Identifier());

    assertThat(child.patientIdentifier()).isEqualTo("p1");
  }

  @Test
  void refusesANullPatientIdentifier() {
    assertThatThrownBy(() -> HandlerContext.of(List.of(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
