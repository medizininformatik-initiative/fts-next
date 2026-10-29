package care.smith.fts.util;

import static care.smith.fts.util.fhir.FhirUtils.stringToFhirResource;

import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Consent.ConsentState;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;

/** Bundles of a patient and its consent, for consent evaluation tests. */
interface ConsentBundles {

  String PATIENT_IDENTIFIER_SYSTEM = "http://hospital.com/patient";
  String POLICY_SYSTEM = "http://hospital.com/policy";

  static Bundle generateBundleWithConsent(String patientIdentifier, String... policies) {
    return bundleWithProvisions(
        patientIdentifier,
        Stream.of(policies)
            .map(ConsentBundles::permittedProvisionComponent)
            .toArray(Consent.ProvisionComponent[]::new));
  }

  static Bundle bundleWithProvisions(
      String patientIdentifier, Consent.ProvisionComponent... provisions) {
    return bundleWithProvisions(patientIdentifier, ConsentState.ACTIVE, provisions);
  }

  static Bundle bundleWithProvisions(
      String patientIdentifier, ConsentState status, Consent.ProvisionComponent... provisions) {
    var patient = new Patient();
    patient.addIdentifier(
        new Identifier().setSystem(PATIENT_IDENTIFIER_SYSTEM).setValue(patientIdentifier));

    var consent = new Consent().setStatus(status);
    var mainProvision = new Consent.ProvisionComponent().setType(Consent.ConsentProvisionType.DENY);
    Stream.of(provisions).forEach(mainProvision::addProvision);
    consent.setProvision(mainProvision);

    var bundle = new Bundle();
    bundle.addEntry().setResource(patient);
    bundle.addEntry().setResource(consent);
    return bundle;
  }

  static Consent.ProvisionComponent policyAProvision(Period period) {
    return new Consent.ProvisionComponent()
        .setType(Consent.ConsentProvisionType.PERMIT)
        .setCode(
            List.of(
                new CodeableConcept()
                    .addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode("POLICY_A"))))
        .setPeriod(period);
  }

  /**
   * Parses the provision from JSON, as a consent response is read, so HAPI keeps the dates as given
   * instead of normalizing them like its setters do.
   */
  static Consent.ProvisionComponent parsedProvision(String policy, String start, String end) {
    var json =
        """
        {"resourceType": "Consent", "provision": {"provision": [{
          "type": "permit",
          "period": {"start": "%s", "end": "%s"},
          "code": [{"coding": [{"system": "%s", "code": "%s"}]}]
        }]}}\
        """
            .formatted(start, end, POLICY_SYSTEM, policy);
    return stringToFhirResource(Consent.class, json).getProvision().getProvisionFirstRep();
  }

  static Extension dataAbsentReason() {
    return new Extension(
        "http://hl7.org/fhir/StructureDefinition/data-absent-reason", new CodeType("unknown"));
  }

  static Consent.ProvisionComponent permittedProvisionComponent(String policy) {
    return provisionComponent(Consent.ConsentProvisionType.PERMIT, policy);
  }

  static Consent.ProvisionComponent deniedProvisionComponent(String policy) {
    return provisionComponent(Consent.ConsentProvisionType.DENY, policy);
  }

  private static Consent.ProvisionComponent provisionComponent(
      Consent.ConsentProvisionType type, String policy) {
    return new Consent.ProvisionComponent()
        .setType(type)
        .setCode(
            List.of(
                new CodeableConcept()
                    .addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode(policy))))
        .setPeriod(new Period().setStart(new Date(0)).setEnd(new Date(1)));
  }
}
