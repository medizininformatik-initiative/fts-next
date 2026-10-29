package care.smith.fts.util;

import static care.smith.fts.util.ConsentBundles.PATIENT_IDENTIFIER_SYSTEM;
import static care.smith.fts.util.ConsentBundles.POLICY_SYSTEM;
import static care.smith.fts.util.ConsentBundles.bundleWithProvisions;
import static care.smith.fts.util.ConsentBundles.deniedProvisionComponent;
import static care.smith.fts.util.ConsentBundles.parsedProvision;
import static care.smith.fts.util.ConsentBundles.permittedProvisionComponent;
import static care.smith.fts.util.MinimalConsents.minimalConsents;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.LenientErrorHandler;
import java.util.Date;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Consent.ConsentState;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

class MinimalConsentsTest {

  private static final RequiredPolicies POLICIES_A_B =
      new RequiredPolicies(POLICY_SYSTEM, Set.of("POLICY_A", "POLICY_B"));

  @Test
  void patientKeepsOnlyIdAndIdentifiers() {
    var bundle = bundleWithProvisions("12345", permittedProvisionComponent("POLICY_A"));
    var patient = patient(bundle).setBirthDate(new Date(0));
    patient.setId("patient-1");
    patient.addName().setFamily("Doe");

    var minimal = patient(minimalConsents(POLICIES_A_B, bundle));

    assertThat(minimal.getIdPart()).isEqualTo("patient-1");
    assertThat(minimal.getIdentifierFirstRep().getSystem()).isEqualTo(PATIENT_IDENTIFIER_SYSTEM);
    assertThat(minimal.getIdentifierFirstRep().getValue()).isEqualTo("12345");
    assertThat(minimal.hasName()).isFalse();
    assertThat(minimal.hasBirthDate()).isFalse();
  }

  @Test
  void otherResourcesAreDropped() {
    var bundle = bundleWithProvisions("12345", permittedProvisionComponent("POLICY_A"));
    bundle.addEntry().setResource(new QuestionnaireResponse());

    var minimal = minimalConsents(POLICIES_A_B, bundle);

    assertThat(minimal.getEntry())
        .<Class<?>>extracting(e -> e.getResource().getClass())
        .containsExactly(Patient.class, Consent.class);
  }

  @Test
  void consentKeepsOnlyWhatIsNeededToClassify() {
    var bundle = bundleWithProvisions("12345", permittedProvisionComponent("POLICY_A"));
    var consent = consent(bundle);
    consent.setId("consent-1");
    consent.setPatient(new Reference("Patient/patient-1"));
    consent.setDateTime(new Date(0));
    consent.setSource(new Reference("DocumentReference/scan"));
    consent.addExtension(new Extension("http://example.com/signature", new StringType("x")));

    var minimal = consent(minimalConsents(POLICIES_A_B, bundle));

    assertThat(minimal.getIdPart()).isEqualTo("consent-1");
    assertThat(minimal.getStatus()).isEqualTo(ConsentState.ACTIVE);
    assertThat(minimal.getPatient().getReference()).isEqualTo("Patient/patient-1");
    assertThat(minimal.hasDateTime()).isFalse();
    assertThat(minimal.hasSource()).isFalse();
    assertThat(minimal.hasExtension()).isFalse();
  }

  @Test
  void provisionsKeepOnlyPermitsOfRequiredPolicies() {
    var other = permittedProvisionComponent("POLICY_C");
    var both = permittedProvisionComponent("POLICY_A");
    both.getCodeFirstRep().addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode("POLICY_C"));
    both.addActor().setReference(new Reference("Organization/org"));
    var bundle = bundleWithProvisions("12345", both, other, deniedProvisionComponent("POLICY_B"));

    var provisions = consent(minimalConsents(POLICIES_A_B, bundle)).getProvision().getProvision();

    assertThat(provisions)
        .singleElement()
        .satisfies(p -> {
          assertThat(p.getType()).isEqualTo(Consent.ConsentProvisionType.PERMIT);
          assertThat(p.getCode())
              .flatExtracting(c -> c.getCoding())
              .extracting(Coding::getCode)
              .containsExactly("POLICY_A");
          assertThat(p.getPeriod().getStart()).isEqualTo(both.getPeriod().getStart());
          assertThat(p.hasActor()).isFalse();
        });
  }

  @Test
  void inactiveConsentsAreDropped() {
    var bundle =
        bundleWithProvisions(
            "12345", ConsentState.INACTIVE, permittedProvisionComponent("POLICY_A"));

    var minimal = minimalConsents(POLICIES_A_B, bundle);

    assertThat(typedResourceStream(minimal, Consent.class)).isEmpty();
  }

  @Test
  void classificationOfCoveredPatientIsKept() {
    var bundle =
        bundleWithProvisions(
            "12345",
            permittedProvisionComponent("POLICY_A"),
            permittedProvisionComponent("POLICY_B"));

    assertThat(POLICIES_A_B.classify(minimalConsents(POLICIES_A_B, bundle)))
        .isEqualTo(POLICIES_A_B.classify(bundle));
  }

  @Test
  void malformedDateIsKeptAsReceived() {
    var json =
        """
        {"resourceType": "Bundle", "entry": [{"resource": {
          "resourceType": "Consent", "status": "active", "provision": {"provision": [{
            "type": "permit", "period": {"start": "2024/02/23"},
            "code": [{"coding": [{"system": "%s", "code": "POLICY_A"}]}]
          }]}}}]}\
        """
            .formatted(POLICY_SYSTEM);
    var bundle =
        FhirContext.forR4Cached()
            .newJsonParser()
            .setParserErrorHandler(new LenientErrorHandler(false).setErrorOnInvalidValue(false))
            .parseResource(Bundle.class, json);

    var minimal = consent(minimalConsents(POLICIES_A_B, bundle));

    assertThat(minimal.getProvision().getProvisionFirstRep().getPeriod().getStartElement())
        .extracting(DateTimeType::getValueAsString)
        .isEqualTo("2024/02/23");
  }

  @Test
  void classificationOfFaultyPatientIsKept() {
    var unreadable = parsedProvision("POLICY_B", "2024-02-23T10:00:00", "2054-01-31");
    var bundle = bundleWithProvisions("12345", permittedProvisionComponent("POLICY_A"), unreadable);

    var classification = POLICIES_A_B.classify(minimalConsents(POLICIES_A_B, bundle));

    assertThat(classification)
        .isEqualTo(POLICIES_A_B.classify(bundle))
        .isInstanceOf(RequiredPolicies.Faulty.class);
  }

  private static Patient patient(Bundle bundle) {
    return typedResourceStream(bundle, Patient.class).findFirst().orElseThrow();
  }

  private static Consent consent(Bundle bundle) {
    return typedResourceStream(bundle, Consent.class).findFirst().orElseThrow();
  }
}
