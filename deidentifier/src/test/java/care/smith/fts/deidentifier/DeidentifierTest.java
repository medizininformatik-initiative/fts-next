package care.smith.fts.deidentifier;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DeidentifierTest {

  private static final RuleSet KEEP_ALL = resource -> (path, element) -> new Rule.Apply(List.of());

  /** Keeps the listed paths unchanged and removes all other elements. */
  private static RuleSet keeping(String... paths) {
    return applying(Arrays.stream(paths).collect(toMap(identity(), path -> List.of())));
  }

  /** Applies the handler chain of each listed path and removes all other elements. */
  private static RuleSet applying(Map<String, List<DeidentifierHandler<Object>>> chains) {
    return resource ->
        (path, element) ->
            Optional.ofNullable(chains.get(String.join(".", path)))
                .<Rule>map(Rule.Apply::new)
                .orElse(Rule.REMOVE);
  }

  private static DeidentifierHandler<Object> replacingWith(String value) {
    return (element, context) -> Optional.of(new StringType(value));
  }

  /**
   * The traversal does not know whether a rule set is an allow list or a deny list. It asks with
   * the element of the input resource, so a rule set can match elements by identity.
   */
  @Test
  void keepsWhatTheRuleSetKeepsAndAsksWithTheInputElement() {
    Patient patient = new Patient();
    patient.setBirthDateElement(new DateType("1970-05-12"));
    List<Base> asked = new ArrayList<>();
    RuleSet recording =
        resource ->
            (path, element) -> {
              asked.add(element);
              return new Rule.Apply(List.of());
            };

    Patient result = (Patient) new Deidentifier(recording).deidentify(patient).orElseThrow();

    assertThat(result.getBirthDateElement().getValueAsString()).isEqualTo("1970-05-12");
    assertThat(asked).singleElement().isSameAs(patient.getBirthDateElement());
  }

  @Test
  void removesWhatTheRuleSetRemoves() {
    Patient patient = new Patient();
    patient.setId("123");
    patient.setGender(AdministrativeGender.FEMALE);
    patient.setBirthDateElement(new DateType("1970-05-12"));
    patient.addName(new HumanName().setFamily("Doe").addGiven("Jane"));

    Patient result =
        (Patient)
            new Deidentifier(keeping("Patient.id", "Patient.gender"))
                .deidentify(patient)
                .orElseThrow();

    assertThat(result.getIdPart()).isEqualTo("123");
    assertThat(result.getGender()).isEqualTo(AdministrativeGender.FEMALE);
    assertThat(result.hasBirthDate()).isFalse();
    assertThat(result.hasName()).isFalse();
  }

  @Test
  void returnsEmptyWhenTheRuleSetRemovesEveryElement() {
    Patient patient = new Patient();
    patient.setId("123");

    assertThat(new Deidentifier(keeping()).deidentify(patient)).isEmpty();
  }

  @Test
  void appliesTheHandlersOfTheRule() {
    Patient patient = new Patient();
    patient.addName(new HumanName().setFamily("Doe"));
    RuleSet ruleSet = applying(Map.of("Patient.name.family", List.of(replacingWith("REDACTED"))));

    Patient result = (Patient) new Deidentifier(ruleSet).deidentify(patient).orElseThrow();

    assertThat(result.getNameFirstRep().getFamily()).isEqualTo("REDACTED");
  }

  /**
   * A handler that removes the value ends the chain; the next handler never sees a {@code null}.
   */
  @Test
  void stopsAHandlerChainAtTheFirstHandlerThatRemovesTheValue() {
    DeidentifierHandler<Object> removes = (value, context) -> Optional.empty();
    Patient patient = new Patient();
    patient.addName(new HumanName().setFamily("Doe"));
    RuleSet ruleSet =
        applying(Map.of("Patient.name.family", List.of(removes, replacingWith("REDACTED"))));

    assertThat(new Deidentifier(ruleSet).deidentify(patient)).isEmpty();
  }

  /**
   * A handler may return a value that its field does not accept: a new StringType for
   * Annotation.text, a MarkdownType field. The error has to name the path.
   */
  @Test
  void namesThePathWhenAHandlerResultDoesNotFitTheField() {
    Observation observation = new Observation();
    observation.addNote().setText("free text");
    RuleSet ruleSet = applying(Map.of("Observation.note.text", List.of(replacingWith("x"))));

    assertThatThrownBy(() -> new Deidentifier(ruleSet).deidentify(observation))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Observation.note.text")
        .hasMessageContaining("StringType")
        .hasMessageContaining("MarkdownType");
  }

  @Test
  void namesChoiceTypePathsByTheConcreteType() {
    Observation observation = new Observation();
    observation.setStatus(Observation.ObservationStatus.FINAL);
    observation.setValue(
        new Quantity().setValue(42).setUnit("mg").setSystem("http://unitsofmeasure.org"));
    RuleSet ruleSet =
        keeping("Observation.value[Quantity].value", "Observation.value[Quantity].unit");

    Observation result =
        (Observation) new Deidentifier(ruleSet).deidentify(observation).orElseThrow();

    assertThat(result.getValueQuantity().getValue()).isEqualByComparingTo("42");
    assertThat(result.getValueQuantity().getUnit()).isEqualTo("mg");
    assertThat(result.getValueQuantity().hasSystem()).isFalse();
    assertThat(result.hasStatus()).isFalse();
  }

  /** A de-identifier must never keep a narrative, and it must not fall over one either. */
  @Test
  void dropsTheNarrativeOfAResourceEvenIfTheRuleSetKeepsIt() {
    Patient patient = new Patient();
    patient.setId("123");
    patient.getText().setStatus(Narrative.NarrativeStatus.GENERATED);
    patient.getText().setDivAsString("<div xmlns=\"http://www.w3.org/1999/xhtml\">Jane Doe</div>");

    Patient result = (Patient) new Deidentifier(KEEP_ALL).deidentify(patient).orElseThrow();

    assertThat(result.getIdPart()).isEqualTo("123");
    assertThat(result.hasText()).isFalse();
  }

  /** HAPI holds no such child; a plain Java value must stop the walk, not pass through. */
  @Test
  void rejectsAChildThatIsNotAFhirElement() {
    Patient patient =
        new Patient() {
          private final Object plain = "Jane Doe";

          @Override
          protected void listChildren(List<Property> children) {
            children.add(new Property("plain", "string", "", 0, 1, List.of()));
          }
        };

    assertThatThrownBy(() -> new Deidentifier(KEEP_ALL).deidentify(patient))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unexpected element of type class java.lang.String");
  }

  private static Extension birthNote() {
    return new Extension("https://example.org/note", new StringType("a note"));
  }

  private static Extension genderDetail() {
    return new Extension(
        "https://example.org/ga", new Coding("https://example.org/s", "D", "divers"));
  }

  /**
   * The extension of a primitive is an element like any other: the rule set decides on its parts.
   */
  @Test
  void keepsThePartsOfAnInputExtensionThatTheRuleSetKeeps() {
    Patient patient = new Patient();
    patient.setGender(AdministrativeGender.OTHER);
    patient.getGenderElement().addExtension(genderDetail());
    RuleSet ruleSet =
        keeping(
            "Patient.gender",
            "Patient.gender.extension.url",
            "Patient.gender.extension.value[Coding].code");

    Patient result = (Patient) new Deidentifier(ruleSet).deidentify(patient).orElseThrow();

    assertThat(result.getGender()).isEqualTo(AdministrativeGender.OTHER);
    assertThat(result.getGenderElement().getExtension())
        .singleElement()
        .satisfies(
            extension -> {
              assertThat(extension.getUrl()).isEqualTo("https://example.org/ga");
              Coding coding = (Coding) extension.getValue();
              assertThat(coding.getCode()).isEqualTo("D");
              assertThat(coding.hasSystem()).isFalse();
              assertThat(coding.hasDisplay()).isFalse();
            });
  }

  /** A primitive whose value the rule set removes is still there as a carrier of its extension. */
  @Test
  void keepsTheExtensionOfAPrimitiveWhoseValueTheRuleSetRemoves() {
    Patient patient = new Patient();
    patient.setGender(AdministrativeGender.OTHER);
    patient.getGenderElement().addExtension(genderDetail());
    RuleSet ruleSet =
        keeping("Patient.gender.extension.url", "Patient.gender.extension.value[Coding].code");

    Patient result = (Patient) new Deidentifier(ruleSet).deidentify(patient).orElseThrow();

    assertThat(result.getGenderElement().hasValue()).isFalse();
    assertThat(result.getGenderElement().getExtension())
        .singleElement()
        .satisfies(
            extension -> {
              assertThat(extension.getUrl()).isEqualTo("https://example.org/ga");
              assertThat(((Coding) extension.getValue()).getCode()).isEqualTo("D");
            });
  }

  /** A handler that builds a fresh value does not take the kept input extensions with it. */
  @Test
  void keepsTheInputExtensionWhenAHandlerReturnsAFreshPrimitive() {
    Patient patient = new Patient();
    patient.setBirthDateElement(new DateType("1970-05-12"));
    patient.getBirthDateElement().addExtension(birthNote());
    DeidentifierHandler<Object> fresh = (value, context) -> Optional.of(new DateType("1970-01-01"));
    RuleSet ruleSet =
        applying(
            Map.of(
                "Patient.birthDate", List.of(fresh),
                "Patient.birthDate.extension.url", List.of(),
                "Patient.birthDate.extension.value[string]", List.of()));

    Patient result = (Patient) new Deidentifier(ruleSet).deidentify(patient).orElseThrow();

    assertThat(result.getBirthDateElement().getValueAsString()).isEqualTo("1970-01-01");
    assertThat(result.getBirthDateElement().getExtension())
        .singleElement()
        .satisfies(
            extension -> {
              assertThat(extension.getUrl()).isEqualTo("https://example.org/note");
              assertThat(extension.getValue().primitiveValue()).isEqualTo("a note");
            });
  }

  /** A handler can add an extension to the value; it does not survive, the input extension does. */
  @Test
  void dropsAnExtensionAHandlerAddsToAPrimitive() {
    Patient patient = new Patient();
    patient.setBirthDateElement(new DateType("1970-05-12"));
    patient.getBirthDateElement().addExtension(birthNote());
    DeidentifierHandler<Object> adding =
        (value, context) -> {
          DateType date = ((DateType) value).copy();
          date.addExtension(new Extension("https://example.org/added", new StringType("added")));
          return Optional.of(date);
        };
    RuleSet ruleSet =
        applying(
            Map.of(
                "Patient.birthDate", List.of(adding),
                "Patient.birthDate.extension.url", List.of(),
                "Patient.birthDate.extension.value[string]", List.of()));

    Patient result = (Patient) new Deidentifier(ruleSet).deidentify(patient).orElseThrow();

    assertThat(result.getBirthDateElement().getExtension())
        .singleElement()
        .satisfies(
            extension -> assertThat(extension.getUrl()).isEqualTo("https://example.org/note"));
  }

  /**
   * A handler under an extension of a primitive sees the primitive among its ancestors, between the
   * resource and the extension.
   */
  @Test
  void passesTheInputPrimitiveAsAncestorToTheHandlersOfItsExtensions() {
    Patient patient = new Patient();
    patient.setBirthDateElement(new DateType("1970-05-12"));
    Extension extension = birthNote();
    patient.getBirthDateElement().addExtension(extension);
    List<List<Base>> seen = new ArrayList<>();
    DeidentifierHandler<Object> recording =
        (value, context) -> {
          seen.add(context.ancestors());
          return Optional.of(value);
        };
    RuleSet ruleSet =
        applying(Map.of("Patient.birthDate.extension.value[string]", List.of(recording)));

    new Deidentifier(ruleSet).deidentify(patient);

    assertThat(seen)
        .singleElement()
        .satisfies(
            ancestors -> {
              assertThat(ancestors).hasSize(3);
              assertThat(ancestors.get(0)).isSameAs(patient);
              assertThat(ancestors.get(1)).isSameAs(patient.getBirthDateElement());
              assertThat(ancestors.get(2)).isSameAs(extension);
            });
  }

  static Stream<Arguments> additionsToAPrimitive() {
    return Stream.of(
        Arguments.of("element id", (Consumer<DateType>) date -> date.setId("the-element-id")),
        Arguments.of(
            "extension",
            (Consumer<DateType>)
                date ->
                    date.addExtension(
                        new Extension("http://example.org/secret", new StringType("secret")))));
  }

  /**
   * The rule set is not asked about the element id of a primitive, so it is dropped. An extension
   * is dropped unless the rule set keeps a path below it.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("additionsToAPrimitive")
  void keepsOnlyTheValueOfAKeptPrimitive(String addition, Consumer<DateType> add) {
    DateType birthDate = new DateType("1970-05-12");
    add.accept(birthDate);
    Patient patient = new Patient();
    patient.setBirthDateElement(birthDate);

    Patient result =
        (Patient) new Deidentifier(keeping("Patient.birthDate")).deidentify(patient).orElseThrow();

    assertThat(result.getBirthDateElement().getValueAsString()).isEqualTo("1970-05-12");
    assertThat(result.getBirthDateElement().getId()).isNull();
    assertThat(result.getBirthDateElement().hasExtension()).isFalse();
  }
}
