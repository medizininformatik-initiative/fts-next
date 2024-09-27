package care.smith.fts.tca.consent;

import static care.smith.fts.util.ConsentedPatientExtractor.hasAllPolicies;
import static care.smith.fts.util.FhirUtils.resourceStream;
import static care.smith.fts.util.FhirUtils.toBundle;
import static care.smith.fts.util.FhirUtils.typedResourceStream;

import com.google.common.base.Predicates;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Patient;

public interface GicsFhirUtil {

  /**
   * Filters an outer Bundle based on the provided policies. The outer Bundle contains Bundles that
   * in turn contain with Consent, Patient, and others. More info can be found here: <a
   * href="https://www.ths-greifswald.de/wp-content/uploads/tools/fhirgw/ig/2023-1-2/ImplementationGuide-markdown-Einwilligungsmanagement-Operations-allConsentsForDomain.html">...</a>
   *
   * @param policiesToCheck the set of policies to check
   * @param outerBundle the outer Bundle to filter
   * @return a filtered Bundle
   */
  static Bundle filterOuterBundle(
      String policySystem, Set<String> policiesToCheck, Bundle outerBundle) {
    return typedResourceStream(outerBundle, Bundle.class)
        .filter(b -> hasAllPolicies(policySystem, b, policiesToCheck))
        .map(GicsFhirUtil::filterInnerBundle)
        .collect(toBundle())
        .setTotal(outerBundle.getTotal());
  }

  /**
   * Filters an inner Bundle to include only Patient and Consent resources.
   *
   * @param b the inner Bundle to filter
   * @return a filtered Bundle
   */
  private static Bundle filterInnerBundle(Bundle b) {
    return resourceStream(b)
        .filter(Predicates.or(Patient.class::isInstance, Consent.class::isInstance))
        .collect(toBundle());
  }
}