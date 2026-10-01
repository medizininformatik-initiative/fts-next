package care.smith.fts.deidentifier.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.deidentifier.internal.HapiReflection.FhirChild;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class HapiReflectionTest {

  /** HAPI stores the {@code Encounter.class} element in a field named {@code class_}. */
  static Stream<Arguments> resources() {
    Patient patient = new Patient();
    patient.setActive(true);
    patient.addName().setFamily("Doe");
    Encounter encounter = new Encounter();
    encounter.setClass_(new Coding().setCode("AMB"));
    return Stream.of(
        Arguments.of("Patient", patient), Arguments.of("Encounter with class_", encounter));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("resources")
  void childrenRoundTripIntoAFreshInstance(String name, Base original) {
    Base copy = HapiReflection.newEmptyInstance(original.getClass());
    for (FhirChild child : HapiReflection.childrenWithValue(original)) {
      child.copyInto(copy, child.value());
    }

    assertThat(copy.equalsDeep(original)).isTrue();
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

  /** Metadata names a choice element {@code value[x]}; the path names the concrete type. */
  @Test
  void namesAChoiceElementByItsConcreteType() {
    Observation observation = new Observation();
    observation.setStatus(Observation.ObservationStatus.FINAL);
    observation.setValue(new Quantity(42));

    List<String> pathElements =
        HapiReflection.childrenWithValue(observation).stream()
            .map(child -> HapiReflection.toPathElement(child.property(), child.value()))
            .toList();

    assertThat(pathElements).containsExactly("status", "value[Quantity]");
  }

  /** Annotation.text is a MarkdownType field, so it does not accept a StringType. */
  @Test
  void copyIntoNamesTheFieldThatDoesNotAcceptTheValue() {
    Annotation annotation = new Annotation().setText("free text");
    FhirChild text = HapiReflection.childrenWithValue(annotation).getFirst();

    assertThatThrownBy(() -> text.copyInto(new Annotation(), new StringType("x")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("text holds MarkdownType, not StringType");
  }

  /** A property in the metadata without a field of the same name is a model HAPI does not have. */
  @Test
  void childrenWithValueNamesAPropertyWithoutAField() {
    Patient patient =
        new Patient() {
          @Override
          protected void listChildren(List<Property> children) {
            children.add(new Property("unknown", "string", "", 0, 1, List.of()));
          }
        };

    assertThatThrownBy(() -> HapiReflection.childrenWithValue(patient))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'unknown'");
  }

  /** {@link Integer} has no no-arg constructor, and the constructor of the other class throws. */
  @ParameterizedTest
  @ValueSource(classes = {Integer.class, FailingConstructor.class})
  void newEmptyInstanceNamesTheClassItCannotInstantiate(Class<?> type) {
    assertThatThrownBy(() -> HapiReflection.newEmptyInstance(type))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(type.getName());
  }

  public static class FailingConstructor {
    public FailingConstructor() {
      throw new UnsupportedOperationException();
    }
  }
}
