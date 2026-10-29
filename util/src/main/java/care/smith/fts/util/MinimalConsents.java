package care.smith.fts.util;

import static care.smith.fts.util.RequiredPolicies.permitProvisions;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static java.util.Objects.requireNonNull;

import java.util.function.Predicate;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Consent.ConsentState;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * Reduces a bundle of a patient and its consents to what {@link RequiredPolicies#classify} and the
 * patient identifier extraction read, so no other patient or consent data leaves the trust center.
 */
public interface MinimalConsents {

  /**
   * The patients of the bundle with their ids and identifiers only, and their active consents with
   * the permit provisions of required policies only.
   */
  public static Bundle minimalConsents(RequiredPolicies requiredPolicies, Bundle bundle) {
    return minimalConsents(requiredPolicies, i -> true, bundle);
  }

  /** Like {@link #minimalConsents(RequiredPolicies, Bundle)}, keeping identifiers of one system. */
  public static Bundle minimalConsents(
      RequiredPolicies requiredPolicies, String identifierSystem, Bundle bundle) {
    return minimalConsents(requiredPolicies, ofSystem(identifierSystem), bundle);
  }

  private static Bundle minimalConsents(
      RequiredPolicies requiredPolicies, Predicate<Identifier> keepIdentifier, Bundle bundle) {
    var patients =
        typedResourceStream(bundle, Patient.class).map(p -> minimalPatient(p, keepIdentifier));
    var consents =
        typedResourceStream(bundle, Consent.class)
            .filter(c -> c.getStatus() == ConsentState.ACTIVE)
            .map(c -> minimalConsent(requiredPolicies, c));
    return Stream.<Resource>concat(patients, consents).collect(toBundle(bundle.getType()));
  }

  /**
   * The patients of the bundle with their ids and their identifiers of the given system only,
   * without their consents.
   */
  public static Bundle minimalPatients(String identifierSystem, Bundle bundle) {
    return typedResourceStream(bundle, Patient.class)
        .map(p -> minimalPatient(p, ofSystem(identifierSystem)))
        .collect(toBundle(bundle.getType()));
  }

  private static Predicate<Identifier> ofSystem(String identifierSystem) {
    requireNonNull(identifierSystem, "Identifier system cannot be null");
    return i -> identifierSystem.equals(i.getSystem());
  }

  private static Patient minimalPatient(Patient patient, Predicate<Identifier> keepIdentifier) {
    var minimal = new Patient();
    minimal.setIdElement(patient.getIdElement().copy());
    patient.getIdentifier().stream()
        .filter(keepIdentifier)
        .forEach(i -> minimal.addIdentifier(i.copy()));
    return minimal;
  }

  private static Consent minimalConsent(RequiredPolicies requiredPolicies, Consent consent) {
    var provision = new Consent.ProvisionComponent().setType(consent.getProvision().getType());
    permitProvisions(consent)
        .map(p -> minimalProvision(requiredPolicies, p))
        .filter(Consent.ProvisionComponent::hasCode)
        .forEach(provision::addProvision);
    var minimal = new Consent().setStatus(consent.getStatus()).setProvision(provision);
    minimal.setIdElement(consent.getIdElement().copy());
    if (consent.hasPatient()) {
      minimal.setPatient(new Reference(consent.getPatient().getReference()));
    }
    return minimal;
  }

  /** The provision's type, period and codings of required policies; no code if none required. */
  private static Consent.ProvisionComponent minimalProvision(
      RequiredPolicies requiredPolicies, Consent.ProvisionComponent provision) {
    var minimal = new Consent.ProvisionComponent().setType(provision.getType());
    if (provision.hasPeriod()) {
      // Shares the period, as copying re-parses its dates and fails on an unreadable one, which
      // has to reach the clinical domain agent as received.
      minimal.setPeriod(provision.getPeriod());
    }
    provision.getCode().stream()
        .map(c -> requiredCodings(requiredPolicies, c))
        .filter(CodeableConcept::hasCoding)
        .forEach(minimal::addCode);
    return minimal;
  }

  private static CodeableConcept requiredCodings(
      RequiredPolicies requiredPolicies, CodeableConcept concept) {
    var required = new CodeableConcept();
    concept.getCoding().stream()
        .filter(requiredPolicies::isRequired)
        .map(Coding::copy)
        .forEach(required::addCoding);
    return required;
  }
}
