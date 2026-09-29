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

  /** The rule set is not asked about the element id or the extensions of a primitive. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("additionsToAPrimitive")
  void keepsOnlyTheValueOfAKeptPrimitive(String addition, Consumer<DateType> add) {
    DateType birthDate = new DateType("1970-05-12");
    add.accept(birthDate);
    Patient patient = new Patient();
    patient.setBirthDateElement(birthDate);

    Patient result = (Patient) new Deidentifier(KEEP_ALL).deidentify(patient).orElseThrow();

    assertThat(result.getBirthDateElement().getValueAsString()).isEqualTo("1970-05-12");
    assertThat(result.getBirthDateElement().getId()).isNull();
    assertThat(result.getBirthDateElement().hasExtension()).isFalse();
  }
}
