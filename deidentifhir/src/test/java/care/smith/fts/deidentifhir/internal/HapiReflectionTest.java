package care.smith.fts.deidentifhir.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.deidentifhir.internal.HapiReflection.FhirChild;
import java.util.List;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class HapiReflectionTest {

  @Test
  void childrenRoundTripIntoAFreshInstance() {
    Patient patient = new Patient();
    patient.setActive(true);
    patient.addName().setFamily("Doe");

    Patient copy = HapiReflection.newEmptyInstance(Patient.class);
    for (FhirChild child : HapiReflection.childrenWithValue(patient)) {
      child.copyInto(copy, child.value());
    }

    assertThat(copy.getActive()).isTrue();
    assertThat(copy.getNameFirstRep().getFamily()).isEqualTo("Doe");
  }

  @Test
  void childrenWithValueSkipsAbsentFields() {
    Patient patient = new Patient();
    patient.setActive(true);

    List<String> names =
        HapiReflection.childrenWithValue(patient).stream()
            .map(child -> child.property().getName())
            .toList();

    assertThat(names).containsExactly("active");
  }

  @Test
  void getChildReadsAValueAndReportsAbsenceAsEmpty() {
    Patient patient = new Patient();
    patient.addIdentifier().setSystem("sys").setValue("42");

    assertThat(HapiReflection.getChild(patient, "identifier").orElseThrow())
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .hasSize(1);
    assertThat(HapiReflection.getChild(patient, "name")).isEmpty();
  }

  /** HAPI stores the {@code Encounter.class} element in a field named {@code class_}. */
  @Test
  void resolvesElementsWhoseFhirNameIsAJavaKeyword() {
    Encounter encounter = new Encounter();
    encounter.setClass_(new Coding().setCode("AMB"));

    Encounter copy = HapiReflection.newEmptyInstance(Encounter.class);
    for (FhirChild child : HapiReflection.childrenWithValue(encounter)) {
      child.copyInto(copy, child.value());
    }

    assertThat(copy.getClass_().getCode()).isEqualTo("AMB");
  }

  @Test
  void getChildNamesTheClassInTheErrorForAnUnknownElement() {
    assertThatThrownBy(() -> HapiReflection.getChild(new Patient(), "nonsense"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nonsense")
        .hasMessageContaining("Patient");
  }

  @Test
  void newEmptyInstanceFailsForAClassWithoutANoArgConstructor() {
    assertThatThrownBy(() -> HapiReflection.newEmptyInstance(Integer.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(Integer.class.getName());
  }

  @Test
  void newEmptyInstanceFailsWhenTheConstructorThrows() {
    assertThatThrownBy(() -> HapiReflection.newEmptyInstance(FailingConstructor.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(FailingConstructor.class.getName());
  }

  public static class FailingConstructor {
    public FailingConstructor() {
      throw new UnsupportedOperationException();
    }
  }
}
