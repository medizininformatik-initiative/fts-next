package care.smith.fts.tca.consent;

import static care.smith.fts.util.MinimalConsents.minimalConsents;
import static care.smith.fts.util.MinimalConsents.minimalPatients;
import static care.smith.fts.util.fhir.FhirClientUtils.fetchCapabilityStatementOperations;
import static care.smith.fts.util.fhir.FhirClientUtils.requireOperations;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;

import care.smith.fts.util.MinimalConsents;
import care.smith.fts.util.RequiredPolicies;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public interface GicsFhirUtil {
  List<String> GICS_OPERATIONS = List.of("allConsentsForDomain", "allConsentsForPerson");

  static Mono<CapabilityStatement> verifyGicsCapabilities(WebClient gicsClient) {
    return fetchCapabilityStatementOperations(gicsClient)
        .flatMap(c1 -> requireOperations(c1, GICS_OPERATIONS));
  }

  /**
   * Filters an outer Bundle based on the provided policies. The outer Bundle contains Bundles that
   * in turn contain with Consent, Patient, and others. Patients whose consents cover all policies
   * remain, as do patients missing a policy only because of a faulty consent, so the clinical
   * domain agent can report them. Each remaining inner Bundle is reduced to what the clinical
   * domain agent needs to classify the patient, see {@link MinimalConsents}. More info can be found
   * here: <a
   * href="https://www.ths-greifswald.de/wp-content/uploads/tools/fhirgw/ig/2023-1-2/ImplementationGuide-markdown-Einwilligungsmanagement-Operations-allConsentsForDomain.html">...</a>
   *
   * @param policiesToCheck the set of policies to check
   * @param outerBundle the outer Bundle to filter
   * @return a filtered Bundle
   */
  static Bundle filterOuterBundle(
      String policySystem, Set<String> policiesToCheck, Bundle outerBundle) {
    var requiredPolicies = new RequiredPolicies(policySystem, policiesToCheck);
    return filterInnerBundles(outerBundle, b -> consentedOrFaulty(requiredPolicies, b));
  }

  /**
   * Filters an outer Bundle of requested patients like {@link #filterOuterBundle}, but keeps the
   * other patients without their consents, so the clinical domain agent can report a requested
   * patient without consent apart from one gICS does not know. Patients keep only identifiers of
   * the requested patient identifier system.
   */
  static Bundle filterRequestedOuterBundle(
      String policySystem,
      Set<String> policiesToCheck,
      String patientIdentifierSystem,
      Bundle outerBundle) {
    var requiredPolicies = new RequiredPolicies(policySystem, policiesToCheck);
    return filterInnerBundles(
        outerBundle,
        b ->
            Optional.of(b)
                .filter(requiredPolicies::consentedOrFaulty)
                .map(inner -> minimalConsents(requiredPolicies, patientIdentifierSystem, inner))
                .or(() -> Optional.of(minimalPatients(patientIdentifierSystem, b))));
  }

  private static Optional<Bundle> consentedOrFaulty(RequiredPolicies requiredPolicies, Bundle b) {
    return Optional.of(b)
        .filter(requiredPolicies::consentedOrFaulty)
        .map(inner -> minimalConsents(requiredPolicies, inner));
  }

  private static Bundle filterInnerBundles(
      Bundle outerBundle, Function<Bundle, Optional<Bundle>> filter) {
    return typedResourceStream(outerBundle, Bundle.class)
        .map(filter)
        .flatMap(Optional::stream)
        .collect(toBundle(outerBundle.getType()))
        .setTotal(outerBundle.getTotal());
  }
}
