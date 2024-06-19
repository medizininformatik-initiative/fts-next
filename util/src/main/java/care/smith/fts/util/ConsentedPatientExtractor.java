package care.smith.fts.util;

import static care.smith.fts.util.FhirUtils.typedResourceStream;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatient.ConsentedPolicies;
import care.smith.fts.api.Period;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.*;

public class ConsentedPatientExtractor {

  public static Stream<ConsentedPatient> extractConsentedPatients(
      String patientIdentifierSystem,
      String policySystem,
      Bundle outerBundle,
      Set<String> policiesToCheck) {
    Stream<Bundle> resources = typedResourceStream(outerBundle, Bundle.class);
    return getConsentedPatients(patientIdentifierSystem, policySystem, resources, policiesToCheck);
  }

  private static Stream<ConsentedPatient> getConsentedPatients(
      String patientIdentifierSystem,
      String policySystem,
      Stream<Bundle> bundles,
      Set<String> policiesToCheck) {
    return bundles
        .map(
            b -> extractConsentedPatient(patientIdentifierSystem, policySystem, b, policiesToCheck))
        .filter(Optional::isPresent)
        .map(Optional::get);
  }

  /**
   * Extract the ConsentedPatient from bundle
   *
   * @param bundle the ConsentedPatient is extracted from
   * @param policiesToCheck the policies the patient has to consent
   * @return a {@link ConsentedPatient}, if all policiesToCheck are consented to
   */
  public static Optional<ConsentedPatient> extractConsentedPatient(
      String patientIdentifierSystem,
      String policySystem,
      Bundle bundle,
      Set<String> policiesToCheck) {
    Optional<String> optionalPid = getPid(bundle, patientIdentifierSystem);
    if (optionalPid.isEmpty()) {
      return Optional.empty();
    }
    String pid = optionalPid.get();

    var consentedPolicies = getConsentedPolicies(policySystem, bundle, policiesToCheck);
    if (consentedPolicies.hasAllPolicies(policiesToCheck)) {
      return Optional.of(new ConsentedPatient(pid, consentedPolicies));
    } else {
      return Optional.empty();
    }
  }

  public static boolean hasAllPolicies(
      String policySystem, Bundle bundle, Set<String> policiesToCheck) {
    var consentedPolicies = getConsentedPolicies(policySystem, bundle, policiesToCheck);
    return consentedPolicies.hasAllPolicies(policiesToCheck);
  }

  private static Optional<String> getPid(Bundle bundle, String patientIdentifierSystem) {
    return typedResourceStream(bundle, Patient.class)
        .flatMap(p -> p.getIdentifier().stream())
        .filter(id -> id.getSystem().equals(patientIdentifierSystem))
        .map(Identifier::getValue)
        .findFirst();
  }

  private static ConsentedPolicies getConsentedPolicies(
      String policySystem, Bundle bundle, Set<String> policiesToCheck) {
    return getPermitProvisionsStream(bundle)
        .map(
            (provisionComponent) ->
                getConsentedPoliciesFromProvision(
                    policySystem, provisionComponent, policiesToCheck))
        .reduce(
            new ConsentedPolicies(),
            (a, b) -> {
              a.merge(b);
              return a;
            });
  }

  private static Stream<Consent.provisionComponent> getPermitProvisionsStream(Bundle bundle) {
    return typedResourceStream(bundle, Consent.class)
        .flatMap(c -> c.getProvision().getProvision().stream());
  }

  private static ConsentedPolicies getConsentedPoliciesFromProvision(
      String policySystem,
      Consent.provisionComponent provisionComponent,
      Set<String> policiesToCheck) {
    String start = provisionComponent.getPeriod().getStartElement().asStringValue();
    String end = provisionComponent.getPeriod().getEndElement().asStringValue();

    var code = provisionComponent.getCode();
    var consentedPolicies = new ConsentedPolicies();
    code.stream()
        .flatMap(c -> extractPolicyFromCode(policySystem, policiesToCheck, c))
        .distinct()
        .forEach(p -> consentedPolicies.put(p, Period.parse(start, end)));
    return consentedPolicies;
  }

  private static Stream<String> extractPolicyFromCode(
      String policySystem, Set<String> policiesToCheck, CodeableConcept c) {
    return c.getCoding().stream()
        .filter(coding -> coding.getSystem().equals(policySystem))
        .map(Coding::getCode)
        .filter(policiesToCheck::contains);
  }
}
