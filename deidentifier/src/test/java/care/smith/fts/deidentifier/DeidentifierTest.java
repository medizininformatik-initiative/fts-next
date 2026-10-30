package care.smith.fts.deidentifier;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.deidentifier.internal.PseudonymUuid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IdType;
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
import org.junit.jupiter.params.provider.ValueSource;

class DeidentifierTest {

  private static final RuleSet KEEP_ALL = resource -> (path, element) -> new Rule.Apply(List.of());

  private static final BundleEntryUrls NO_REFERENCE_RULE = BundleEntryUrls.none();

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

    Patient result =
        (Patient)
            new Deidentifier(recording, NO_REFERENCE_RULE).deidentify(patient, "p").orElseThrow();

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
            new Deidentifier(keeping("Patient.id", "Patient.gender"), NO_REFERENCE_RULE)
                .deidentify(patient, "p")
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

    assertThat(new Deidentifier(keeping(), NO_REFERENCE_RULE).deidentify(patient, "p")).isEmpty();
  }

  @Test
  void appliesTheHandlersOfTheRule() {
    Patient patient = new Patient();
    patient.addName(new HumanName().setFamily("Doe"));
    RuleSet ruleSet = applying(Map.of("Patient.name.family", List.of(replacingWith("REDACTED"))));

    Patient result =
        (Patient)
            new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentify(patient, "p").orElseThrow();

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

    assertThat(new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentify(patient, "p")).isEmpty();
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

    assertThatThrownBy(
            () -> new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentify(observation, "p"))
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
        (Observation)
            new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentify(observation, "p").orElseThrow();

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

    Patient result =
        (Patient)
            new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE).deidentify(patient, "p").orElseThrow();

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

    assertThatThrownBy(() -> new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE).deidentify(patient, "p"))
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

    Patient result =
        (Patient)
            new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentify(patient, "p").orElseThrow();

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

    Patient result =
        (Patient)
            new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentify(patient, "p").orElseThrow();

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

    Patient result =
        (Patient)
            new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentify(patient, "p").orElseThrow();

    assertThat(result.getBirthDateElement().getValueAsString()).isEqualTo("1970-01-01");
    assertThat(result.getBirthDateElement().getExtension())
        .singleElement()
        .satisfies(
            extension -> {
              assertThat(extension.getUrl()).isEqualTo("https://example.org/note");
              assertThat(extension.getValue().primitiveValue()).isEqualTo("a note");
            });
  }

  /** An extension a handler adds to the value follows the kept input extensions. */
  @Test
  void keepsAnExtensionAHandlerAddsToAPrimitive() {
    var patient = new Patient();
    patient.setBirthDateElement(new DateType("1970-05-12"));
    patient.getBirthDateElement().addExtension(birthNote());
    DeidentifierHandler<Object> adding =
        (value, context) -> {
          var date = ((DateType) value).copy();
          date.addExtension(new Extension("https://example.org/added", new StringType("added")));
          return Optional.of(date);
        };
    var ruleSet =
        applying(
            Map.of(
                "Patient.birthDate", List.of(adding),
                "Patient.birthDate.extension.url", List.of(),
                "Patient.birthDate.extension.value[string]", List.of()));

    var result =
        (Patient)
            new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentify(patient, "p").orElseThrow();

    assertThat(result.getBirthDateElement().getExtension())
        .extracting(Extension::getUrl)
        .containsExactly("https://example.org/note", "https://example.org/added");
  }

  /**
   * The date-shift pattern: the handler takes the date value away and puts a transport id on the
   * element as an extension. The rule set keeps no extension path, so the extension is not subject
   * to it.
   */
  @Test
  void keepsTheElementOfAHandlerThatReplacesTheValueWithAnExtension() {
    var patient = new Patient();
    patient.setBirthDateElement(new DateType("1970-05-12"));
    DeidentifierHandler<Object> shifting =
        (value, context) -> {
          var date = new DateType();
          date.addExtension(new Extension("https://example.org/added", new StringType("id-1")));
          return Optional.of(date);
        };
    var ruleSet = applying(Map.of("Patient.birthDate", List.of(shifting)));

    var result =
        (Patient)
            new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentify(patient, "p").orElseThrow();

    assertThat(result.hasBirthDateElement()).isTrue();
    assertThat(result.getBirthDateElement().hasValue()).isFalse();
    assertThat(result.getBirthDateElement().getExtension())
        .singleElement()
        .satisfies(
            extension -> {
              assertThat(extension.getUrl()).isEqualTo("https://example.org/added");
              assertThat(extension.getValue().primitiveValue()).isEqualTo("id-1");
            });
  }

  /** A handler that removes the value leaves nothing behind when no input extension is kept. */
  @Test
  void removesAPrimitiveWhoseHandlerRemovesItWhenNoInputExtensionIsKept() {
    var patient = new Patient();
    patient.setBirthDateElement(new DateType("1970-05-12"));
    patient.setGender(AdministrativeGender.OTHER);
    DeidentifierHandler<Object> removing = (value, context) -> Optional.empty();
    var ruleSet =
        applying(Map.of("Patient.birthDate", List.of(removing), "Patient.gender", List.of()));

    var result =
        (Patient)
            new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentify(patient, "p").orElseThrow();

    assertThat(result.hasBirthDateElement()).isFalse();
  }

  /** The handler gets the value without the input extensions, the rule set decides on those. */
  @Test
  void dropsInputExtensionsTheRuleSetDoesNotKeepWhenAHandlerReturnsTheValue() {
    var patient = new Patient();
    patient.setBirthDateElement(new DateType("1970-05-12"));
    patient.getBirthDateElement().addExtension(birthNote());
    DeidentifierHandler<Object> identity = (value, context) -> Optional.of(value);
    var ruleSet = applying(Map.of("Patient.birthDate", List.of(identity)));

    var result =
        (Patient)
            new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentify(patient, "p").orElseThrow();

    assertThat(result.getBirthDateElement().getValueAsString()).isEqualTo("1970-05-12");
    assertThat(result.getBirthDateElement().hasExtension()).isFalse();
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

    new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentify(patient, "p");

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

  @Test
  void rejectsARuleSetThatReturnsNoRules() {
    RuleSet noRules = resource -> null;

    assertThatThrownBy(
            () -> new Deidentifier(noRules, NO_REFERENCE_RULE).deidentify(new Patient(), "p"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("returned no rules");
  }

  /** Siblings share their parent, so a handler that keeps state per context treats them alike. */
  @Test
  void passesOneContextToTheHandlersOfSiblingElements() {
    var patient = new Patient();
    patient.addName().getPeriod().setStartElement(new DateTimeType("2020-01-01"));
    patient.getNameFirstRep().getPeriod().setEndElement(new DateTimeType("2020-12-31"));
    var seen = new ArrayList<HandlerContext>();
    DeidentifierHandler<Object> recording =
        (value, context) -> {
          seen.add(context);
          return Optional.of(value);
        };
    var ruleSet =
        applying(
            Map.of(
                "Patient.name.period.start", List.of(recording),
                "Patient.name.period.end", List.of(recording)));

    new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentify(patient, "p");

    assertThat(seen).hasSize(2);
    assertThat(seen.get(0)).isSameAs(seen.get(1));
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
        (Patient)
            new Deidentifier(keeping("Patient.birthDate"), NO_REFERENCE_RULE)
                .deidentify(patient, "p")
                .orElseThrow();

    assertThat(result.getBirthDateElement().getValueAsString()).isEqualTo("1970-05-12");
    assertThat(result.getBirthDateElement().getId()).isNull();
    assertThat(result.getBirthDateElement().hasExtension()).isFalse();
  }

  @Test
  void deidentifiesEachBundleEntryAndKeepsTheSurvivorsInOrder() {
    var bundle = new Bundle();
    bundle.addEntry().setResource(new Patient().setGender(AdministrativeGender.FEMALE));
    bundle.addEntry().setResource(new Observation().setStatus(Observation.ObservationStatus.FINAL));
    bundle.addEntry().setResource(new Patient().setGender(AdministrativeGender.MALE));
    var ruleSet = keeping("Patient.gender", "Observation.status");

    var result = new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentifyBundle(bundle, "p");

    assertThat(result.getEntry())
        .extracting(entry -> entry.getResource().fhirType())
        .containsExactly("Patient", "Observation", "Patient");
    assertThat(((Patient) result.getEntry().get(2).getResource()).getGender())
        .isEqualTo(AdministrativeGender.MALE);
  }

  @Test
  void dropsABundleEntryWhoseResourceTheRuleSetRemovesEntirely() {
    var bundle = new Bundle();
    bundle.addEntry().setResource(new Patient().setGender(AdministrativeGender.FEMALE));
    bundle.addEntry().setResource(new Observation().setStatus(Observation.ObservationStatus.FINAL));

    var result =
        new Deidentifier(keeping("Observation.status"), NO_REFERENCE_RULE)
            .deidentifyBundle(bundle, "p");

    assertThat(result.getEntry())
        .singleElement()
        .satisfies(entry -> assertThat(entry.getResource().fhirType()).isEqualTo("Observation"));
  }

  @Test
  void keepsTheTypeOfABundle() {
    var bundle = new Bundle().setType(Bundle.BundleType.COLLECTION);

    var result = new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE).deidentifyBundle(bundle, "p");

    assertThat(result.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
  }

  @Test
  void leavesTheTypeOfABundleWithoutOneUnset() {
    var result = new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE).deidentifyBundle(new Bundle(), "p");

    assertThat(result.hasType()).isFalse();
  }

  @Test
  void keepsOnlyTheTypeWhenNoEntrySurvives() {
    var bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
    bundle.setId("b1");
    bundle.setTimestamp(new Date());
    bundle.getMeta().setSource("src");
    bundle.setTotal(1);
    var entry = bundle.addEntry();
    entry.setFullUrl("urn:uuid:1");
    entry.getRequest().setUrl("Patient");
    entry.setResource(new Patient().setGender(AdministrativeGender.FEMALE));

    var result = new Deidentifier(keeping(), NO_REFERENCE_RULE).deidentifyBundle(bundle, "p");

    assertThat(result.getType()).isEqualTo(Bundle.BundleType.TRANSACTION);
    assertThat(result.hasEntry()).isFalse();
    assertThat(result.hasId()).isFalse();
    assertThat(result.hasMeta()).isFalse();
    assertThat(result.hasTimestamp()).isFalse();
    assertThat(result.hasTotal()).isFalse();
  }

  @Test
  void dropsTheFullUrlOfASurvivingEntryWithoutAReferenceRule() {
    var bundle = new Bundle();
    bundle
        .addEntry()
        .setFullUrl("urn:uuid:1")
        .setResource(new Patient().setGender(AdministrativeGender.FEMALE));

    var result = new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE).deidentifyBundle(bundle, "p");

    assertThat(result.getEntryFirstRep().hasFullUrl()).isFalse();
    assertThat(result.getEntryFirstRep().hasResource()).isTrue();
  }

  @Test
  void keepsTheFullUrlOfASurvivingEntryAsTheUuidOfThePseudonym() {
    var bundle = new Bundle();
    bundle
        .addEntry()
        .setFullUrl("urn:uuid:source-chosen")
        .setResource(new Patient().setGender(AdministrativeGender.FEMALE));

    var result =
        new Deidentifier(KEEP_ALL, BundleEntryUrls.of(urlRecording(new ArrayList<>())))
            .deidentifyBundle(bundle, "p");

    assertThat(result.getEntryFirstRep().getFullUrl())
        .isEqualTo("urn:uuid:" + PseudonymUuid.uuidFrom("Patient/p-1"));
    assertThat(bundle.getEntryFirstRep().getFullUrl()).isEqualTo("urn:uuid:source-chosen");
  }

  @Test
  void showsTheReferenceHandlerTheBundleAndTheEntryOfTheFullUrl() {
    var bundle = new Bundle();
    var entry =
        bundle
            .addEntry()
            .setFullUrl("urn:uuid:1")
            .setResource(new Patient().setGender(AdministrativeGender.FEMALE));
    var seen = new ArrayList<HandlerContext>();

    new Deidentifier(KEEP_ALL, BundleEntryUrls.of(urlRecording(seen)))
        .deidentifyBundle(bundle, "p1");

    assertThat(seen)
        .singleElement()
        .satisfies(
            context -> {
              assertThat(context.patientIdentifier()).isEqualTo("p1");
              assertThat(context.parent()).isSameAs(entry);
              assertThat(context.ancestors()).containsExactly(bundle, entry);
            });
  }

  @Test
  void keepsAnEntryWithoutFullUrlWithoutOne() {
    var bundle = new Bundle();
    bundle.addEntry().setResource(new Patient().setGender(AdministrativeGender.FEMALE));
    var seen = new ArrayList<HandlerContext>();

    var result =
        new Deidentifier(KEEP_ALL, BundleEntryUrls.of(urlRecording(seen)))
            .deidentifyBundle(bundle, "p");

    assertThat(result.getEntryFirstRep().hasFullUrl()).isFalse();
    assertThat(seen).isEmpty();
  }

  @Test
  void givesTwoEntriesWithTheSamePseudonymTheSameFullUrl() {
    var bundle = new Bundle();
    bundle
        .addEntry()
        .setFullUrl("urn:uuid:a")
        .setResource(new Patient().setGender(AdministrativeGender.FEMALE));
    bundle
        .addEntry()
        .setFullUrl("urn:uuid:b")
        .setResource(new Patient().setGender(AdministrativeGender.FEMALE));

    var result =
        new Deidentifier(KEEP_ALL, BundleEntryUrls.of(urlRecording(new ArrayList<>())))
            .deidentifyBundle(bundle, "p");

    assertThat(result.getEntry())
        .extracting(BundleEntryComponent::getFullUrl)
        .containsOnly("urn:uuid:" + PseudonymUuid.uuidFrom("Patient/p-1"))
        .hasSize(2);
  }

  /** A bundle with one patient entry whose request has {@code method} and {@code url}. */
  private static Bundle patientEntryWithRequest(HTTPVerb method, String url) {
    var bundle = new Bundle();
    var entry = bundle.addEntry();
    entry.setResource(new Patient().setGender(AdministrativeGender.FEMALE).setId("123"));
    entry.getRequest().setMethod(method).setUrl(url);
    return bundle;
  }

  @Test
  void keepsTheRequestOfAnEntryWhoseUrlNamesOnlyTheType() {
    var bundle = patientEntryWithRequest(HTTPVerb.POST, "Patient");

    var result = new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE).deidentifyBundle(bundle, "p");

    var request = result.getEntryFirstRep().getRequest();
    assertThat(request.getMethod()).isEqualTo(HTTPVerb.POST);
    assertThat(request.getUrl()).isEqualTo("Patient");
  }

  @Test
  void takesTheIdOfTheDeidentifiedResourceForATypeAndIdUrl() {
    var bundle = patientEntryWithRequest(HTTPVerb.PUT, "Patient/123");
    DeidentifierHandler<Object> pseudonymizing = (id, context) -> Optional.of(new IdType("x9"));
    var ruleSet =
        applying(Map.of("Patient.id", List.of(pseudonymizing), "Patient.gender", List.of()));

    var result = new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentifyBundle(bundle, "p");

    var request = result.getEntryFirstRep().getRequest();
    assertThat(request.getMethod()).isEqualTo(HTTPVerb.PUT);
    assertThat(request.getUrl()).isEqualTo("Patient/x9");
  }

  @Test
  void dropsTheRequestWhenTheResourceLosesItsId() {
    var bundle = patientEntryWithRequest(HTTPVerb.PUT, "Patient/123");

    var result =
        new Deidentifier(keeping("Patient.gender"), NO_REFERENCE_RULE)
            .deidentifyBundle(bundle, "p");

    assertThat(result.getEntryFirstRep().hasRequest()).isFalse();
    assertThat(result.getEntryFirstRep().hasResource()).isTrue();
  }

  /** Without a reference rule the request goes and the resource stays. */
  @Test
  void dropsTheRequestWhenItsConditionalUrlNeedsTheReferenceRule() {
    var bundle = patientEntryWithRequest(HTTPVerb.POST, "Patient?identifier=sys|12345");

    var result = new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE).deidentifyBundle(bundle, "p");

    assertThat(result.getEntryFirstRep().hasRequest()).isFalse();
    assertThat(result.getEntryFirstRep().hasResource()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Patient?name=Doe",
        "Patient/123/_history/2",
        "https://server.example/fhir/Patient/123",
        ""
      })
  void failsTheBundleWhenTheRequestUrlHasAFormatThatCannotBePseudonymized(String url) {
    var bundle = patientEntryWithRequest(HTTPVerb.POST, url);
    var deidentifier = new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE);

    assertThatThrownBy(() -> deidentifier.deidentifyBundle(bundle, "p"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A request url has a format that cannot be pseudonymized.");
  }

  @Test
  void dropsARequestWithoutUrl() {
    var bundle = new Bundle();
    var entry = bundle.addEntry().setResource(new Patient().setGender(AdministrativeGender.FEMALE));
    entry.getRequest().setMethod(HTTPVerb.POST);

    var result = new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE).deidentifyBundle(bundle, "p");

    assertThat(result.getEntryFirstRep().hasRequest()).isFalse();
  }

  @Test
  void keepsOnlyMethodAndUrlOfARequest() {
    var bundle = patientEntryWithRequest(HTTPVerb.POST, "Patient");
    bundle
        .getEntryFirstRep()
        .getRequest()
        .setIfNoneExist("identifier=sys|12345")
        .setIfMatch("W/\"1\"")
        .setIfNoneMatch("*")
        .setIfModifiedSince(new Date());

    var result = new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE).deidentifyBundle(bundle, "p");

    var request = result.getEntryFirstRep().getRequest();
    assertThat(request.hasIfNoneExist()).isFalse();
    assertThat(request.hasIfMatch()).isFalse();
    assertThat(request.hasIfNoneMatch()).isFalse();
    assertThat(request.hasIfModifiedSince()).isFalse();
    assertThat(request.getUrl()).isEqualTo("Patient");
  }

  @Test
  void leavesAnEntryWithoutRequestWithoutRequest() {
    var bundle = new Bundle();
    var entry = bundle.addEntry().setResource(new Patient().setGender(AdministrativeGender.FEMALE));

    var result = new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE).deidentifyBundle(bundle, "p");

    assertThat(result.getEntryFirstRep().hasRequest()).isFalse();
    assertThat(entry.hasRequest()).isFalse();
  }

  @Test
  void showsHandlersInABundleEntryThePatientIdentifierAndTheEntryAsResource() {
    var patient = new Patient().setGender(AdministrativeGender.FEMALE);
    var bundle = new Bundle();
    bundle.addEntry().setResource(patient);
    var seen = new ArrayList<HandlerContext>();
    DeidentifierHandler<Object> recording =
        (value, context) -> {
          seen.add(context);
          return Optional.of(value);
        };
    var ruleSet = applying(Map.of("Patient.gender", List.of(recording)));

    new Deidentifier(ruleSet, NO_REFERENCE_RULE).deidentifyBundle(bundle, "p1");

    assertThat(seen)
        .singleElement()
        .satisfies(
            context -> {
              assertThat(context.patientIdentifier()).isEqualTo("p1");
              assertThat(context.resource()).isSameAs(patient);
              assertThat(context.ancestors()).containsExactly(patient);
            });
  }

  private static DeidentifierHandler<StringType> urlRecording(List<HandlerContext> seen) {
    return (value, context) -> {
      seen.add(context);
      return Optional.of(new StringType("Patient/p-1"));
    };
  }

  @Test
  void showsTheReferenceHandlerTheBundleTheEntryAndTheRequestOfTheUrl() {
    var bundle = patientEntryWithRequest(HTTPVerb.PUT, "Patient?identifier=sys|12345");
    var entry = bundle.getEntryFirstRep();
    var seen = new ArrayList<HandlerContext>();

    new Deidentifier(KEEP_ALL, BundleEntryUrls.of(urlRecording(seen)))
        .deidentifyBundle(bundle, "p1");

    assertThat(seen)
        .singleElement()
        .satisfies(
            context -> {
              assertThat(context.patientIdentifier()).isEqualTo("p1");
              assertThat(context.resource()).isSameAs(bundle);
              assertThat(context.parent()).isSameAs(entry.getRequest());
              assertThat(context.ancestors()).containsExactly(bundle, entry, entry.getRequest());
            });
  }

  @Test
  void keepsTheConditionalUrlOfAResourceEntryThePseudonymizedUrl() {
    var bundle = patientEntryWithRequest(HTTPVerb.PUT, "Patient?identifier=sys|12345");

    var result =
        new Deidentifier(KEEP_ALL, BundleEntryUrls.of(urlRecording(new ArrayList<>())))
            .deidentifyBundle(bundle, "p");

    var request = result.getEntryFirstRep().getRequest();
    assertThat(request.getMethod()).isEqualTo(HTTPVerb.PUT);
    assertThat(request.getUrl()).isEqualTo("Patient/p-1");
    assertThat(result.getEntryFirstRep().hasResource()).isTrue();
  }

  private static Bundle deleteEntry(String url) {
    var bundle = new Bundle();
    var entry = bundle.addEntry().setFullUrl("urn:uuid:1");
    entry.getRequest().setMethod(HTTPVerb.DELETE).setUrl(url).setIfMatch("W/\"1\"");
    return bundle;
  }

  @Test
  void neverAsksTheReferenceHandlerForTheFullUrlOfADeleteEntry() {
    var seen = new ArrayList<HandlerContext>();

    new Deidentifier(KEEP_ALL, BundleEntryUrls.of(urlRecording(seen)))
        .deidentifyBundle(deleteEntry("Patient/123"), "p");

    assertThat(seen).hasSize(1);
    assertThat(seen.get(0).ancestors()).hasSize(3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Patient/123", "Patient?identifier=sys|12345"})
  void keepsADeleteEntryWithThePseudonymizedUrlAndNoResource(String url) {
    var bundle = deleteEntry(url);

    var result =
        new Deidentifier(KEEP_ALL, BundleEntryUrls.of(urlRecording(new ArrayList<>())))
            .deidentifyBundle(bundle, "p");

    var kept = result.getEntryFirstRep();
    assertThat(result.getEntry()).hasSize(1);
    assertThat(kept.hasResource()).isFalse();
    assertThat(kept.hasFullUrl()).isFalse();
    assertThat(kept.getRequest().getMethod()).isEqualTo(HTTPVerb.DELETE);
    assertThat(kept.getRequest().getUrl()).isEqualTo("Patient/p-1");
    assertThat(kept.getRequest().hasIfMatch()).isFalse();
  }

  @Test
  void showsTheReferenceHandlerTheBundleTheEntryAndTheRequestOfADelete() {
    var bundle = deleteEntry("Patient/123");
    var entry = bundle.getEntryFirstRep();
    var seen = new ArrayList<HandlerContext>();

    new Deidentifier(KEEP_ALL, BundleEntryUrls.of(urlRecording(seen)))
        .deidentifyBundle(bundle, "p1");

    assertThat(seen)
        .singleElement()
        .satisfies(
            context ->
                assertThat(context.ancestors()).containsExactly(bundle, entry, entry.getRequest()));
  }

  @Test
  void dropsATypeOnlyDeleteEntryWithoutAskingTheReferenceHandler() {
    var seen = new ArrayList<HandlerContext>();

    var result =
        new Deidentifier(KEEP_ALL, BundleEntryUrls.of(urlRecording(seen)))
            .deidentifyBundle(deleteEntry("Patient"), "p");

    assertThat(result.getEntry()).isEmpty();
    assertThat(seen).isEmpty();
  }

  @Test
  void dropsADeleteEntryWithoutAReferenceRule() {
    var result =
        new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE)
            .deidentifyBundle(deleteEntry("Patient/123"), "p");

    assertThat(result.getEntry()).isEmpty();
  }

  @Test
  void dropsADeleteEntryWhenTheReferenceHandlerRemovesTheUrl() {
    var entryUrls = BundleEntryUrls.of((value, context) -> Optional.empty());

    var result =
        new Deidentifier(KEEP_ALL, entryUrls).deidentifyBundle(deleteEntry("Patient/123"), "p");

    assertThat(result.getEntry()).isEmpty();
  }

  @Test
  void dropsAnEntryWithoutResourceAndWithoutRequestOrUrlAndLeavesTheInputAlone() {
    var bundle = new Bundle();
    var withoutRequest = bundle.addEntry().setFullUrl("urn:uuid:1");
    var withoutUrl = bundle.addEntry();
    withoutUrl.getRequest().setMethod(HTTPVerb.DELETE);
    var entryUrls = BundleEntryUrls.of(urlRecording(new ArrayList<>()));

    var result = new Deidentifier(KEEP_ALL, entryUrls).deidentifyBundle(bundle, "p");

    assertThat(result.getEntry()).isEmpty();
    assertThat(withoutRequest.hasRequest()).isFalse();
  }

  @Test
  void refusesNullBundleEntryUrls() {
    assertThatThrownBy(() -> new Deidentifier(KEEP_ALL, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void showsHandlersOfASingleResourceThePatientIdentifier() {
    var seen = new ArrayList<HandlerContext>();
    DeidentifierHandler<Object> recording =
        (value, context) -> {
          seen.add(context);
          return Optional.of(value);
        };
    var ruleSet = applying(Map.of("Patient.gender", List.of(recording)));

    new Deidentifier(ruleSet, NO_REFERENCE_RULE)
        .deidentify(new Patient().setGender(AdministrativeGender.FEMALE), "p1");

    assertThat(seen)
        .singleElement()
        .satisfies(context -> assertThat(context.patientIdentifier()).isEqualTo("p1"));
  }

  @Test
  void dropsABundleEntryWithoutAResource() {
    var bundle = new Bundle();
    bundle.addEntry().setFullUrl("urn:uuid:1");
    bundle.addEntry().setResource(new Patient().setGender(AdministrativeGender.FEMALE));

    var result = new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE).deidentifyBundle(bundle, "p");

    assertThat(result.getEntry()).hasSize(1);
  }

  @Test
  void rejectsANestedBundleAndNamesTheEntryIndex() {
    var bundle = new Bundle();
    bundle.addEntry().setResource(new Patient().setGender(AdministrativeGender.FEMALE));
    bundle.addEntry().setResource(new Bundle().setType(Bundle.BundleType.COLLECTION));

    assertThatThrownBy(
            () -> new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE).deidentifyBundle(bundle, "p"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("entry 1")
        .hasMessageContaining("nested bundles are not supported");
  }

  @Test
  void deidentifiesABundleGivenAsResourceLikeDeidentifyBundle() {
    var bundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
    bundle.addEntry().setResource(new Patient().setGender(AdministrativeGender.FEMALE));
    bundle.addEntry().setResource(new Observation().setStatus(Observation.ObservationStatus.FINAL));

    var result =
        new Deidentifier(keeping("Patient.gender"), NO_REFERENCE_RULE).deidentify(bundle, "p");

    assertThat(result)
        .get()
        .isInstanceOfSatisfying(
            Bundle.class,
            deidentified -> {
              assertThat(deidentified.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
              assertThat(deidentified.getEntry())
                  .singleElement()
                  .satisfies(e -> assertThat(e.getResource().fhirType()).isEqualTo("Patient"));
            });
  }

  @Test
  void keepsABundleGivenAsResourceEvenWhenNoEntrySurvives() {
    var bundle = new Bundle();
    bundle.addEntry().setResource(new Patient().setGender(AdministrativeGender.FEMALE));

    assertThat(new Deidentifier(keeping(), NO_REFERENCE_RULE).deidentify(bundle, "p"))
        .get()
        .isInstanceOfSatisfying(Bundle.class, result -> assertThat(result.hasEntry()).isFalse());
  }

  @Test
  void refusesANullResourceOrPatientIdentifier() {
    var deidentifier = new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE);

    assertThatThrownBy(() -> deidentifier.deidentify(null, "p"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> deidentifier.deidentify(new Patient(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void refusesANullBundleOrPatientIdentifier() {
    var deidentifier = new Deidentifier(KEEP_ALL, NO_REFERENCE_RULE);

    assertThatThrownBy(() -> deidentifier.deidentifyBundle(null, "p"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> deidentifier.deidentifyBundle(new Bundle(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
