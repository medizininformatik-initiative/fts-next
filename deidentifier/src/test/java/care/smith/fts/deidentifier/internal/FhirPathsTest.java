package care.smith.fts.deidentifier.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class FhirPathsTest {

  static Stream<Arguments> typedPaths() {
    return Stream.of(
        Arguments.of("Patient.birthDate", DateType.class),
        Arguments.of("Patient.name.family", StringType.class),
        Arguments.of("Observation.value[Quantity]", Quantity.class),
        Arguments.of("Patient.deceased[dateTime]", DateTimeType.class),
        Arguments.of("Encounter.diagnosis", Encounter.DiagnosisComponent.class),
        Arguments.of("Encounter.diagnosis.rank", PositiveIntType.class),
        Arguments.of("Patient.gender.extension.url", UriType.class),
        Arguments.of(
            "Observation.effective[dateTime].extension.value[Coding].code", CodeType.class),
        Arguments.of("Medication.ingredient.extension.extension.value[uri]", UriType.class),
        Arguments.of("Patient.birthDate.extension", Extension.class),
        Arguments.of("Patient.gender", Enumeration.class),
        Arguments.of("Patient.deceased[boolean]", BooleanType.class),
        Arguments.of("Observation.value[CodeableConcept]", CodeableConcept.class));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("typedPaths")
  void resolvesAPathToTheHapiClassOfTheElement(String path, Class<?> expected) {
    assertThat(FhirPaths.elementType(path)).contains(expected);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "Foo.bar",
        "Patient.birthdate",
        "Patient.deceased",
        "Patient.gender.extension.value",
        "Patient.birthDate.foo",
        "Patient.deceased[]",
        "Patient.deceased[dateTime",
        "Patient.[dateTime]",
        "",
        "Patient.",
        "Patient..birthDate",
        "Patient.deceased[DateTime]",
        "Observation.value[quantity]",
        "Patient.birthDate[date]",
        "Patient.birth[Date]",
        "Observation.value[Quantity][x]",
        "Patient.deceasedDateTime",
        "Observation.valueQuantity",
        "Observation.value[Quantity].extension.valueCoding",
        "patient.birthDate",
        "PATIENT.birthDate"
      })
  void isEmptyForAPathThatNamesNoElement(String path) {
    assertThat(FhirPaths.elementType(path)).isEmpty();
  }
}
