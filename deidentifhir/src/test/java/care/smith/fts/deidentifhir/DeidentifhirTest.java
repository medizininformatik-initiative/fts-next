package care.smith.fts.deidentifhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.deidentifhir.handlers.Handlers;
import care.smith.fts.deidentifhir.handlers.IDReplacementProvider;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

class DeidentifhirTest {

  @Test
  void keepsOnlyFieldsListedInBase() {
    Config config =
        ConfigFactory.parseString(
            """
            deidentiFHIR.profile.version=0.2
            modules = {
              patient: {
                pattern = "Patient.exists()"
                base = ["Patient.id", "Patient.gender"]
              }
            }
            """);
    Deidentifhir deidentifhir = Deidentifhir.fromConfig(config);

    Patient patient = new Patient();
    patient.setId("123");
    patient.setGender(Enumerations.AdministrativeGender.FEMALE);
    patient.setBirthDateElement(new DateType("1970-05-12"));
    patient.addName(new HumanName().setFamily("Doe").addGiven("Jane"));

    Patient result = (Patient) deidentifhir.deidentify(patient).orElseThrow();

    assertThat(result.getIdPart()).isEqualTo("123");
    assertThat(result.getGender()).isEqualTo(Enumerations.AdministrativeGender.FEMALE);
    assertThat(result.hasBirthDate()).isFalse();
    assertThat(result.hasName()).isFalse();
  }

  @Test
  void appliesPathHandlerFromRegistry() {
    Config config =
        ConfigFactory.parseString(
            """
            modules = {
              patient: {
                pattern = "Patient.exists()"
                base = ["Patient.name.family"]
                paths = {
                  "Patient.name.family": {handler = testReplace}
                }
              }
            }
            """);
    Registry registry = new Registry();
    registry.addHandler(
        "testReplace",
        (DeidentifhirHandler<org.hl7.fhir.r4.model.StringType>)
            (path, value, context) ->
                Optional.of(new org.hl7.fhir.r4.model.StringType("REDACTED")));
    Deidentifhir deidentifhir = Deidentifhir.fromConfig(config, registry);

    Patient patient = new Patient();
    patient.addName(new HumanName().setFamily("Doe"));

    Patient result = (Patient) deidentifhir.deidentify(patient).orElseThrow();

    assertThat(result.getNameFirstRep().getFamily()).isEqualTo("REDACTED");
  }

  @Test
  void rejectsUnknownHandlerNameAtBuildTime() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = ["Patient.id"]
              paths = { "Patient.id": {handler = doesNotExist} }
            }
            """);

    assertThatThrownBy(() -> Deidentifhir.fromConfig(config, new Registry()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("doesNotExist");
  }

  @Test
  void rejectsPathHandlerOnPathMissingFromBase() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = ["Patient.id"]
              paths = { "Patient.birthDate": {handler = testReplace} }
            }
            """);
    Registry registry = new Registry();
    registry.addHandler("testReplace", (path, value, context) -> Optional.of(value));

    assertThatThrownBy(() -> Deidentifhir.fromConfig(config, registry))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Patient.birthDate");
  }

  @Test
  void returnsNullWhenNoModuleMatchesTheResource() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Observation.exists()"
              base = ["Observation.id"]
            }
            """);
    Deidentifhir deidentifhir = Deidentifhir.fromConfig(config);

    Patient patient = new Patient();
    patient.setId("123");

    assertThat(deidentifhir.deidentify(patient)).isEmpty();
  }

  @Test
  void deidentifiesBundleAndDropsFullyRemovedEntries() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = ["Patient.id"]
            }
            """);
    Deidentifhir deidentifhir = Deidentifhir.fromConfig(config);

    org.hl7.fhir.r4.model.Bundle bundle = new org.hl7.fhir.r4.model.Bundle();
    Patient patient = new Patient();
    patient.setId("123");
    bundle.addEntry().setResource(patient);
    org.hl7.fhir.r4.model.Observation observation = new org.hl7.fhir.r4.model.Observation();
    observation.setId("obs-1");
    bundle.addEntry().setResource(observation);

    org.hl7.fhir.r4.model.Bundle result =
        (org.hl7.fhir.r4.model.Bundle) deidentifhir.deidentify(bundle).orElseThrow();

    assertThat(result.getEntry()).hasSize(1);
    assertThat(result.getEntryFirstRep().getResource().getIdPart()).isEqualTo("123");
  }

  /** A de-identifier must never keep a narrative, and it must not fall over one either. */
  @Test
  void dropsTheNarrativeOfAResource() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = ["Patient.id", "Patient.text"]
            }
            """);
    Patient patient = new Patient();
    patient.setId("123");
    patient.getText().setStatus(Narrative.NarrativeStatus.GENERATED);
    patient.getText().setDivAsString("<div xmlns=\"http://www.w3.org/1999/xhtml\">Jane Doe</div>");

    Patient result = (Patient) Deidentifhir.fromConfig(config).deidentify(patient).orElseThrow();

    assertThat(result.getIdPart()).isEqualTo("123");
    assertThat(result.hasText()).isFalse();
  }

  @Test
  void dropsTheElementIdOfAKeptPrimitive() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = ["Patient.birthDate"]
            }
            """);
    Patient patient = new Patient();
    patient.setBirthDateElement(new DateType("1970-05-12"));
    patient.getBirthDateElement().setId("the-element-id");

    Patient result = (Patient) Deidentifhir.fromConfig(config).deidentify(patient).orElseThrow();

    assertThat(result.getBirthDateElement().getValueAsString()).isEqualTo("1970-05-12");
    assertThat(result.getBirthDateElement().getId()).isNull();
  }

  /** A DELETE entry of a transaction bundle carries a request and no resource (rule bdl-5). */
  @Test
  void keepsAnEntryThatCarriesARequestButNoResource() {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.TRANSACTION);
    bundle.addEntry().getRequest().setMethod(Bundle.HTTPVerb.DELETE).setUrl("Patient/123");

    Bundle result = (Bundle) referringEngine().deidentify(bundle).orElseThrow();

    assertThat(result.getEntry()).hasSize(1);
    assertThat(result.getEntryFirstRep().hasResource()).isFalse();
    assertThat(result.getEntryFirstRep().getRequest().getMethod())
        .isEqualTo(Bundle.HTTPVerb.DELETE);
    assertThat(result.getEntryFirstRep().getRequest().getUrl())
        .isEqualTo("Patient/pseudonym-of-123");
  }

  /** Such an entry would say nothing at all, so it goes the way of an emptied resource. */
  @Test
  void dropsAnEntryThatIsLeftWithNothing() {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.TRANSACTION);
    bundle.addEntry().getRequest().setMethod(Bundle.HTTPVerb.DELETE).setUrl("Patient/123");

    Bundle result = (Bundle) bundleEngine().deidentify(bundle).orElseThrow();

    assertThat(result.getEntry()).isEmpty();
  }

  /**
   * A handler that removes the value ends the chain; the next handler never sees a {@code null}.
   */
  @Test
  void stopsAHandlerChainAtTheFirstHandlerThatRemovesTheValue() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.first {
              pattern = "Patient.exists()"
              base = ["Patient.name.family"]
              paths = { "Patient.name.family": {handler = removes} }
            }
            modules.second {
              pattern = "Patient.exists()"
              base = ["Patient.name.family"]
              paths = { "Patient.name.family": {handler = replaces} }
            }
            """);
    Registry registry = new Registry();
    registry.addHandler(
        "removes",
        (DeidentifhirHandler<org.hl7.fhir.r4.model.StringType>)
            (path, value, context) -> Optional.empty());
    registry.addHandler(
        "replaces",
        (DeidentifhirHandler<org.hl7.fhir.r4.model.StringType>)
            (path, value, context) ->
                Optional.of(
                    new org.hl7.fhir.r4.model.StringType(
                        value.getValue().toUpperCase(Locale.ROOT))));
    Patient patient = new Patient();
    patient.addName(new HumanName().setFamily("Doe"));

    assertThat(Deidentifhir.fromConfig(config, registry).deidentify(patient)).isEmpty();
  }

  @Test
  void deidentifyBundlePassesThePatientIdentifierToTheHandlers() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = ["Patient.gender"]
              paths = { "Patient.gender": {handler = recordPatient} }
            }
            """);
    List<String> seen = new ArrayList<>();
    Registry registry = new Registry();
    registry.addHandler(
        "recordPatient",
        (path, value, context) -> {
          context.patientIdentifier().ifPresent(seen::add);
          return Optional.of(value);
        });
    Bundle bundle = new Bundle().setType(Bundle.BundleType.SEARCHSET);
    bundle
        .addEntry()
        .setResource(new Patient().setGender(Enumerations.AdministrativeGender.FEMALE));

    Deidentifhir.fromConfig(config, registry).deidentifyBundle(bundle, "patient-1");

    assertThat(seen).containsExactly("patient-1");
  }

  /** A bundle is the unit the caller sends on, so its skeleton survives even when no entry does. */
  @Test
  void deidentifyBundleKeepsTheSkeletonWhenNoEntrySurvives() {
    Bundle bundle = new Bundle().setType(Bundle.BundleType.SEARCHSET);
    bundle
        .addEntry()
        .setResource(new Patient().setGender(Enumerations.AdministrativeGender.FEMALE));

    Bundle result =
        Deidentifhir.fromConfig(
                ConfigFactory.parseString(
                    """
                    modules.none {
                      pattern = "Observation.exists()"
                      base = ["Observation.status"]
                    }
                    """))
            .deidentifyBundle(bundle, "patient-1");

    assertThat(result.getType()).isEqualTo(Bundle.BundleType.SEARCHSET);
    assertThat(result.getEntry()).isEmpty();
  }

  @Test
  void keepsTheBundleTypeOfTheInputBundle() {
    Bundle result = (Bundle) bundleEngine().deidentify(transactionBundle()).orElseThrow();

    assertThat(result.getType()).isEqualTo(Bundle.BundleType.TRANSACTION);
  }

  @Test
  void keepsTheRequestOfASurvivingEntry() {
    Bundle result = (Bundle) bundleEngine().deidentify(transactionBundle()).orElseThrow();

    Bundle.BundleEntryRequestComponent request = result.getEntryFirstRep().getRequest();
    assertThat(request.getMethod()).isEqualTo(Bundle.HTTPVerb.POST);
    assertThat(request.getUrl()).isEqualTo("Patient");
  }

  /**
   * The link a transaction bundle resolves on: a reference to an entry, that entry's id and its
   * {@code fullUrl} all carry the pseudonym of the entry's id; the fullUrl shaped into a UUID. The
   * expected fullUrl is the shaping of {@code pseudonym-of-8d1f-42}.
   */
  @Test
  void givesTheFullUrlTheSamePseudonymAsAReferenceToTheEntry() {
    Bundle bundle = transactionBundle();
    Patient referring = new Patient();
    referring.setId("urn:uuid:other");
    referring.setManagingOrganization(new Reference("urn:uuid:8d1f-42"));
    bundle.addEntry().setFullUrl("urn:uuid:other").setResource(referring);

    Bundle result = (Bundle) referringEngine().deidentify(bundle).orElseThrow();

    assertThat(result.getEntryFirstRep().getFullUrl())
        .isEqualTo("urn:uuid:a9fb0f8c-30e2-43ed-8372-6e689ea717d8");
    assertThat(result.getEntryFirstRep().getResource().getIdPart())
        .isEqualTo("pseudonym-of-8d1f-42");
    Patient deidentifiedReferring = (Patient) result.getEntry().get(1).getResource();
    assertThat(deidentifiedReferring.getManagingOrganization().getReference())
        .isEqualTo("Patient/pseudonym-of-8d1f-42");
  }

  @Test
  void givesEqualFullUrlsEqualPseudonymsAndDifferentOnesDifferentPseudonyms() {
    Bundle bundle = transactionBundle();
    Resource sameResourceAgain = bundle.getEntryFirstRep().getResource().copy();
    bundle.addEntry().setFullUrl("urn:uuid:8d1f-42").setResource(sameResourceAgain);
    Patient other = new Patient();
    other.setId("urn:uuid:other");
    bundle.addEntry().setFullUrl("urn:uuid:other").setResource(other);

    Bundle result = (Bundle) referringEngine().deidentify(bundle).orElseThrow();

    assertThat(result.getEntry().get(1).getFullUrl())
        .isEqualTo(result.getEntry().get(0).getFullUrl());
    assertThat(result.getEntry().get(2).getFullUrl())
        .isNotEqualTo(result.getEntry().get(0).getFullUrl());
  }

  /**
   * A conditional create names the resource by an identifier of the source system, so the request
   * url has to be pseudonymized like the reference it is.
   */
  @Test
  void pseudonymizesTheIdentifierOfAConditionalCreateUrl() {
    Bundle result =
        (Bundle)
            referringEngine()
                .deidentify(bundleWithRequestUrl("Patient?identifier=sys|12345"))
                .orElseThrow();

    assertThat(result.getEntryFirstRep().getRequest().getUrl())
        .isEqualTo("Patient?identifier=sys|value");
  }

  @Test
  void pseudonymizesTheIdOfARequestUrlThatNamesOneResource() {
    Bundle result = (Bundle) referringEngine().deidentify(updateBundle("123")).orElseThrow();

    assertThat(result.getEntryFirstRep().getRequest().getUrl())
        .isEqualTo("Patient/pseudonym-of-123");
  }

  @Test
  void keepsARequestUrlThatOnlyNamesAResourceType() {
    Bundle result =
        (Bundle) referringEngine().deidentify(bundleWithRequestUrl("Patient")).orElseThrow();

    assertThat(result.getEntryFirstRep().getRequest().getUrl()).isEqualTo("Patient");
  }

  /** Without a surviving id the url cannot name the resource, and must not leak the source id. */
  @Test
  void dropsTheRequestOfAnUpdateWhoseResourceLostItsId() {
    Deidentifhir deidentifhir =
        Deidentifhir.fromConfig(
            ConfigFactory.parseString(
                """
                modules.patient {
                  pattern = "Patient.exists()"
                  base = ["Patient.gender"]
                }
                """),
            referenceRegistry((resourceType, id) -> "pseudonym-of-" + id));
    Bundle bundle = updateBundle("123");
    ((Patient) bundle.getEntryFirstRep().getResource())
        .setGender(Enumerations.AdministrativeGender.FEMALE);

    Bundle result = (Bundle) deidentifhir.deidentify(bundle).orElseThrow();

    assertThat(result.getEntryFirstRep().hasResource()).isTrue();
    assertThat(result.getEntryFirstRep().hasRequest()).isFalse();
  }

  /**
   * The url of an update names the resource in its body, so it has to carry the id the body
   * carries. A profile that keeps the id unchanged keeps it in the url as well.
   */
  @Test
  void givesAnUpdateUrlTheIdTheProfileGaveTheResource() {
    Deidentifhir deidentifhir =
        Deidentifhir.fromConfig(
            ConfigFactory.parseString(
                """
                modules.patient {
                  pattern = "Patient.exists()"
                  base = ["Patient.id"]
                }
                """),
            referenceRegistry((resourceType, id) -> "pseudonym-of-" + id));

    Bundle result = (Bundle) deidentifhir.deidentify(updateBundle("123")).orElseThrow();

    assertThat(result.getEntryFirstRep().getResource().getIdPart()).isEqualTo("123");
    assertThat(result.getEntryFirstRep().getRequest().getUrl()).isEqualTo("Patient/123");
  }

  /** A transaction bundle with one update of the Patient with the given id. */
  private static Bundle updateBundle(String id) {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.TRANSACTION);
    Patient patient = new Patient();
    patient.setId(id);
    bundle
        .addEntry()
        .setResource(patient)
        .getRequest()
        .setMethod(Bundle.HTTPVerb.PUT)
        .setUrl("Patient/" + id);
    return bundle;
  }

  private static Bundle bundleWithRequestUrl(String url) {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.TRANSACTION);
    Patient patient = new Patient();
    patient.setId("urn:uuid:8d1f-42");
    bundle.addEntry().setResource(patient).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl(url);
    return bundle;
  }

  @Test
  void leavesAnEntryWithoutAFullUrlWithoutOne() {
    Bundle bundle = new Bundle();
    Patient patient = new Patient();
    patient.setId("123");
    bundle.addEntry().setResource(patient);

    Bundle result = (Bundle) bundleEngine().deidentify(bundle).orElseThrow();

    assertThat(result.getEntryFirstRep().hasFullUrl()).isFalse();
  }

  /** A fullUrl element with only an extension has no value to pseudonymize; the entry stays. */
  @Test
  void keepsAnEntryWhoseFullUrlHasNoValue() {
    Bundle bundle = new Bundle();
    Patient patient = new Patient();
    patient.setId("123");
    BundleEntryComponent entry = bundle.addEntry().setResource(patient);
    entry
        .getFullUrlElement()
        .addExtension(
            "http://hl7.org/fhir/StructureDefinition/data-absent-reason", new CodeType("masked"));

    Bundle result = (Bundle) referringEngine().deidentify(bundle).orElseThrow();

    assertThat(result.getEntryFirstRep().hasResource()).isTrue();
    assertThat(result.getEntryFirstRep().hasFullUrl()).isFalse();
  }

  /** A fullUrl that names a server cannot be pseudonymized, and must not survive either. */
  @Test
  void dropsAFullUrlThatIsNotInUrnForm() {
    Bundle bundle = new Bundle();
    Patient patient = new Patient();
    patient.setId("123");
    bundle.addEntry().setFullUrl("https://server.example/fhir/Patient/123").setResource(patient);

    Bundle result = (Bundle) referringEngine().deidentify(bundle).orElseThrow();

    assertThat(result.getEntryFirstRep().hasFullUrl()).isFalse();
  }

  @Test
  void dropsTheFullUrlWhenNoReferenceHandlerIsRegistered() {
    Bundle result = (Bundle) bundleEngine().deidentify(transactionBundle()).orElseThrow();

    assertThat(result.getEntryFirstRep().hasFullUrl()).isFalse();
  }

  /**
   * The MII configurations route conditional references to {@code referenceReplacementHandler}, so
   * that name has to handle them. Splitting this value at its first slash would leave the
   * identifier of the source system in the output.
   */
  @Test
  void rewritesAConditionalReferenceUnderTheReferenceReplacementHandlerName() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.encounter {
              pattern = "Encounter.exists()"
              base = ["Encounter.serviceProvider.reference"]
              paths = {
                "Encounter.serviceProvider.reference": {handler = referenceReplacementHandler}
              }
            }
            """);
    Deidentifhir deidentifhir =
        Deidentifhir.fromConfig(config, referenceRegistry((resourceType, id) -> "id-pseudonym"));

    Encounter encounter = new Encounter();
    encounter.setServiceProvider(
        new Reference("Organization?identifier=https://github.com/synthetichealth/synthea|4fa6"));

    Encounter result = (Encounter) deidentifhir.deidentify(encounter).orElseThrow();

    assertThat(result.getServiceProvider().getReference())
        .isEqualTo("Organization?identifier=https://github.com/synthetichealth/synthea|value");
  }

  /**
   * A urn reference names no resource type. The engine takes it from the entry whose fullUrl it
   * names, so the reference and that entry's id are pseudonymized under the same key and the link
   * survives as a relative reference.
   */
  @Test
  void resolvesAUrnReferenceToTheTypeOfTheEntryItNames() {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.TRANSACTION);
    Patient patient = new Patient();
    patient.setId("urn:uuid:9e2a-7");
    bundle.addEntry().setFullUrl("urn:uuid:9e2a-7").setResource(patient);
    Encounter encounter = new Encounter();
    encounter.setId("urn:uuid:8d1f-42");
    encounter.setSubject(new Reference("urn:uuid:9e2a-7"));
    bundle.addEntry().setFullUrl("urn:uuid:8d1f-42").setResource(encounter);

    Bundle result = (Bundle) urnEngine().deidentify(bundle).orElseThrow();

    Patient deidentifiedPatient = (Patient) result.getEntry().get(0).getResource();
    Encounter deidentifiedEncounter = (Encounter) result.getEntry().get(1).getResource();
    assertThat(deidentifiedPatient.getIdPart()).isEqualTo("pseudonym-of-Patient-9e2a-7");
    assertThat(deidentifiedEncounter.getSubject().getReference())
        .isEqualTo("Patient/pseudonym-of-Patient-9e2a-7");
  }

  /** An engine that pseudonymizes Patient and Encounter ids and Encounter.subject by type and id. */
  private static Deidentifhir urnEngine() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = ["Patient.id"]
              paths = { "Patient.id": {handler = idReplacementHandler} }
            }
            modules.encounter {
              pattern = "Encounter.exists()"
              base = ["Encounter.id", "Encounter.subject.reference"]
              paths = {
                "Encounter.id": {handler = idReplacementHandler}
                "Encounter.subject.reference": {handler = referenceReplacementHandler}
              }
            }
            """);
    return Deidentifhir.fromConfig(
        config,
        referenceRegistry((resourceType, id) -> "pseudonym-of-" + resourceType + "-" + id));
  }

  /** An engine that keeps the id of every Patient and registers no handler at all. */
  private static Deidentifhir bundleEngine() {
    return Deidentifhir.fromConfig(
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = ["Patient.id"]
            }
            """));
  }

  /** The same engine with the handlers that pseudonymize ids, references and full urls. */
  private static Deidentifhir referringEngine() {
    return Deidentifhir.fromConfig(
        bundleConfig(), referenceRegistry((resourceType, id) -> "pseudonym-of-" + id));
  }

  /**
   * A registry with the two handler names the reference configurations of this test use. The
   * identifier provider answers every conditional reference with the same value.
   */
  private static Registry referenceRegistry(IDReplacementProvider idReplacementProvider) {
    Registry registry = new Registry();
    registry.addHandler(
        "idReplacementHandler", Handlers.idReplacementHandler(idReplacementProvider));
    registry.addHandler(
        "referenceReplacementHandler",
        Handlers.referenceReplacementHandler(idReplacementProvider, (system, value) -> "value"));
    return registry;
  }

  private static Config bundleConfig() {
    return ConfigFactory.parseString(
        """
        modules.patient {
          pattern = "Patient.exists()"
          base = ["Patient.id", "Patient.managingOrganization.reference"]
          paths = {
            "Patient.id": {handler = idReplacementHandler}
            "Patient.managingOrganization.reference": {handler = referenceReplacementHandler}
          }
        }
        """);
  }

  /** A transaction bundle with one entry that carries a fullUrl and a request. */
  private static Bundle transactionBundle() {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.TRANSACTION);
    Patient patient = new Patient();
    patient.setId("urn:uuid:8d1f-42");
    bundle
        .addEntry()
        .setFullUrl("urn:uuid:8d1f-42")
        .setResource(patient)
        .getRequest()
        .setMethod(Bundle.HTTPVerb.POST)
        .setUrl("Patient");
    return bundle;
  }

  @Test
  void expandsWildcardPathsAgainstTheBaseList() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = ["Patient.name.family", "Patient.name.given", "Patient.birthDate"]
              paths = { "Patient.name.*": {handler = testReplace} }
            }
            """);
    Registry registry = new Registry();
    registry.addHandler(
        "testReplace",
        (DeidentifhirHandler<org.hl7.fhir.r4.model.StringType>)
            (path, value, context) ->
                Optional.of(new org.hl7.fhir.r4.model.StringType("REDACTED")));
    Deidentifhir deidentifhir = Deidentifhir.fromConfig(config, registry);

    Patient patient = new Patient();
    patient.addName(new HumanName().setFamily("Doe").addGiven("Jane"));
    patient.setBirthDateElement(new DateType("1970-05-12"));

    Patient result = (Patient) deidentifhir.deidentify(patient).orElseThrow();

    assertThat(result.getNameFirstRep().getFamily()).isEqualTo("REDACTED");
    assertThat(result.getNameFirstRep().getGiven().getFirst().getValue()).isEqualTo("REDACTED");
    assertThat(result.getBirthDateElement().getValueAsString()).isEqualTo("1970-05-12");
  }

  @Test
  void appliesTypeHandlerToAllElementsOfThatType() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = ["Patient.id", "Patient.birthDate"]
              types = { "DateType": {handler = generalizeDate} }
            }
            """);
    Registry registry = new Registry();
    registry.addHandler(
        "generalizeDate",
        (DeidentifhirHandler<DateType>)
            (path, value, context) -> Optional.of(new DateType("1970-01-01")));
    Deidentifhir deidentifhir = Deidentifhir.fromConfig(config, registry);

    Patient patient = new Patient();
    patient.setId("123");
    patient.setBirthDateElement(new DateType("1970-05-12"));

    Patient result = (Patient) deidentifhir.deidentify(patient).orElseThrow();

    assertThat(result.getBirthDateElement().getValueAsString()).isEqualTo("1970-01-01");
    assertThat(result.getIdPart()).isEqualTo("123");
  }

  @Test
  void namesChoiceTypePathsByTheConcreteType() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.observation {
              pattern = "Observation.exists()"
              base = ["Observation.value[Quantity].value", "Observation.value[Quantity].unit"]
            }
            """);
    Deidentifhir deidentifhir = Deidentifhir.fromConfig(config);

    org.hl7.fhir.r4.model.Observation observation = new org.hl7.fhir.r4.model.Observation();
    observation.setStatus(org.hl7.fhir.r4.model.Observation.ObservationStatus.FINAL);
    observation.setValue(
        new org.hl7.fhir.r4.model.Quantity()
            .setValue(42)
            .setUnit("mg")
            .setSystem("http://unitsofmeasure.org"));

    org.hl7.fhir.r4.model.Observation result =
        (org.hl7.fhir.r4.model.Observation) deidentifhir.deidentify(observation).orElseThrow();

    assertThat(result.getValueQuantity().getValue()).isEqualByComparingTo("42");
    assertThat(result.getValueQuantity().getUnit()).isEqualTo("mg");
    assertThat(result.getValueQuantity().hasSystem()).isFalse();
    assertThat(result.hasStatus()).isFalse();
  }

  @Test
  void profilePatternSelectsModuleByMetaProfile() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.lab {
              pattern = "Observation.meta.profile contains 'http://example.org/lab'"
              base = ["Observation.id"]
            }
            """);
    Deidentifhir deidentifhir = Deidentifhir.fromConfig(config);

    org.hl7.fhir.r4.model.Observation lab = new org.hl7.fhir.r4.model.Observation();
    lab.setId("lab-1");
    lab.getMeta().addProfile("http://example.org/lab");
    org.hl7.fhir.r4.model.Observation other = new org.hl7.fhir.r4.model.Observation();
    other.setId("other-1");

    assertThat(deidentifhir.deidentify(lab).orElseThrow().getIdPart()).isEqualTo("lab-1");
    assertThat(deidentifhir.deidentify(other)).isEmpty();
  }

  @Test
  void removesNonWhitelistedExtensionsOnKeptPrimitives() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = ["Patient.birthDate"]
            }
            """);
    Deidentifhir deidentifhir = Deidentifhir.fromConfig(config);

    Patient patient = new Patient();
    DateType birthDate = new DateType("1970-05-12");
    birthDate.addExtension(
        new org.hl7.fhir.r4.model.Extension(
            "http://example.org/secret", new org.hl7.fhir.r4.model.StringType("secret")));
    patient.setBirthDateElement(birthDate);

    Patient result = (Patient) deidentifhir.deidentify(patient).orElseThrow();

    assertThat(result.getBirthDateElement().getValueAsString()).isEqualTo("1970-05-12");
    assertThat(result.getBirthDateElement().hasExtension()).isFalse();
  }

  @Test
  void keepsWhitelistedExtensionsOnPrimitives() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = [
                "Patient.birthDate",
                "Patient.birthDate.extension.url",
                "Patient.birthDate.extension.value[string]"
              ]
            }
            """);
    Deidentifhir deidentifhir = Deidentifhir.fromConfig(config);

    Patient patient = new Patient();
    DateType birthDate = new DateType("1970-05-12");
    birthDate.addExtension(
        new org.hl7.fhir.r4.model.Extension(
            "http://example.org/kept", new org.hl7.fhir.r4.model.StringType("kept")));
    patient.setBirthDateElement(birthDate);

    Patient result = (Patient) deidentifhir.deidentify(patient).orElseThrow();

    assertThat(result.getBirthDateElement().getValueAsString()).isEqualTo("1970-05-12");
    assertThat(result.getBirthDateElement().getExtension()).hasSize(1);
    assertThat(result.getBirthDateElement().getExtensionFirstRep().getUrl())
        .isEqualTo("http://example.org/kept");
    assertThat(result.getBirthDateElement().getExtensionFirstRep().getValue().primitiveValue())
        .isEqualTo("kept");
  }

  /** A handler may remove the value; a whitelisted extension then keeps the element alive. */
  @Test
  void keepsAnElementWhoseValueAHandlerRemovedForItsWhitelistedExtension() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = [
                "Patient.birthDate",
                "Patient.birthDate.extension.url",
                "Patient.birthDate.extension.value[string]"
              ]
              paths { "Patient.birthDate" { handler = removeValue } }
            }
            """);
    Registry registry = new Registry();
    registry.addHandler("removeValue", (path, value, context) -> Optional.empty());
    Deidentifhir deidentifhir = Deidentifhir.fromConfig(config, registry);

    Patient patient = new Patient();
    DateType birthDate = new DateType("1970-05-12");
    birthDate.addExtension(
        new org.hl7.fhir.r4.model.Extension(
            "http://example.org/kept", new org.hl7.fhir.r4.model.StringType("kept")));
    patient.setBirthDateElement(birthDate);

    Patient result = (Patient) deidentifhir.deidentify(patient).orElseThrow();

    assertThat(result.getBirthDateElement().getValue()).isNull();
    assertThat(result.getBirthDateElement().getExtensionFirstRep().getUrl())
        .isEqualTo("http://example.org/kept");
    assertThat(result.getBirthDateElement().getExtensionFirstRep().getValue().primitiveValue())
        .isEqualTo("kept");
  }

  @Test
  void keepsExtensionsAHandlerAdds() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = ["Patient.birthDate"]
              paths = {
                "Patient.birthDate": {handler = shiftDate}
              }
            }
            """);
    Registry registry = new Registry();
    registry.addHandler(
        "shiftDate",
        (DeidentifhirHandler<DateType>)
            (path, date, context) -> {
              date.addExtension(
                  "http://example.org/transport-id", new org.hl7.fhir.r4.model.StringType("tid-1"));
              date.setValue(null);
              return Optional.of(date);
            });
    Deidentifhir deidentifhir = Deidentifhir.fromConfig(config, registry);

    Patient patient = new Patient();
    patient.setBirthDateElement(new DateType("1970-05-12"));

    Patient result = (Patient) deidentifhir.deidentify(patient).orElseThrow();

    assertThat(result.getBirthDateElement().getValue()).isNull();
    assertThat(result.getBirthDateElement().getExtension()).hasSize(1);
    assertThat(result.getBirthDateElement().getExtensionFirstRep().getUrl())
        .isEqualTo("http://example.org/transport-id");
  }

  @Test
  void keepsWhitelistedExtensionsWhenTheHandlerReturnsAFreshElement() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = [
                "Patient.name.family",
                "Patient.name.family.extension.url",
                "Patient.name.family.extension.value[string]"
              ]
              paths = {
                "Patient.name.family": {handler = testReplace}
              }
            }
            """);
    Registry registry = new Registry();
    registry.addHandler(
        "testReplace",
        (DeidentifhirHandler<org.hl7.fhir.r4.model.StringType>)
            (path, value, context) ->
                Optional.of(new org.hl7.fhir.r4.model.StringType("REDACTED")));
    Deidentifhir deidentifhir = Deidentifhir.fromConfig(config, registry);

    Patient patient = new Patient();
    org.hl7.fhir.r4.model.StringType family = new org.hl7.fhir.r4.model.StringType("Doe");
    family.addExtension(
        new org.hl7.fhir.r4.model.Extension(
            "http://example.org/kept", new org.hl7.fhir.r4.model.StringType("kept")));
    patient.addName(new HumanName().setFamilyElement(family));

    Patient result = (Patient) deidentifhir.deidentify(patient).orElseThrow();

    assertThat(result.getNameFirstRep().getFamily()).isEqualTo("REDACTED");
    assertThat(result.getNameFirstRep().getFamilyElement().getExtension()).hasSize(1);
    assertThat(result.getNameFirstRep().getFamilyElement().getExtensionFirstRep().getUrl())
        .isEqualTo("http://example.org/kept");
    assertThat(
            result
                .getNameFirstRep()
                .getFamilyElement()
                .getExtensionFirstRep()
                .getValue()
                .primitiveValue())
        .isEqualTo("kept");
  }
}
