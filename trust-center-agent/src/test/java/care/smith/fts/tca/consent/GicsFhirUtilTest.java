package care.smith.fts.tca.consent;

import static care.smith.fts.tca.consent.GicsFhirUtil.filterOuterBundle;
import static care.smith.fts.util.fhir.FhirUtils.resourceStream;
import static care.smith.fts.util.fhir.FhirUtils.stringToFhirResource;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.Set;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.junit.jupiter.api.Test;

class GicsFhirUtilTest {

  private static final String POLICY_SYSTEM = "http://hospital.com/policy";
  private static final Set<String> POLICIES = Set.of("POLICY_A", "POLICY_B");

  @Test
  void onlyInnerBundlesWithAllPoliciesRemain() {
    var outerBundle =
        Stream.of(
                innerBundle("with-all", "POLICY_A", "POLICY_B"),
                innerBundle("with-one", "POLICY_A"))
            .collect(toBundle())
            .setTotal(2);

    var filtered = GicsFhirUtil.filterOuterBundle(POLICY_SYSTEM, POLICIES, outerBundle);

    assertThat(patientIds(filtered)).containsExactly("with-all");
    assertThat(filtered.getTotal()).isEqualTo(2);
  }

  @Test
  void innerBundleWithUnreadableDateIsDroppedAndOthersRemain() {
    var outerBundle =
        Stream.of(
                innerBundleWithUnreadablePolicyB("unreadable"),
                innerBundle("with-all", "POLICY_A", "POLICY_B"))
            .collect(toBundle());

    var filtered = filterOuterBundle(POLICY_SYSTEM, POLICIES, outerBundle);

    assertThat(patientIds(filtered)).containsExactly("with-all");
  }

  @Test
  void innerBundleKeepsOnlyPatientAndConsent() {
    var innerBundle =
        Stream.concat(
                resourceStream(innerBundle("with-all", "POLICY_A", "POLICY_B")),
                Stream.of(new Observation().setId("observation-1")))
            .collect(toBundle());

    var filtered =
        filterOuterBundle(POLICY_SYSTEM, POLICIES, Stream.of(innerBundle).collect(toBundle()));

    var resources =
        typedResourceStream(filtered, Bundle.class).flatMap(b -> resourceStream(b)).toList();
    assertThat(resources).hasSize(2);
    assertThat(resources).hasAtLeastOneElementOfType(Patient.class);
    assertThat(resources).hasAtLeastOneElementOfType(Consent.class);
    assertThat(resources).noneMatch(Observation.class::isInstance);
  }

  private static Stream<String> patientIds(Bundle outerBundle) {
    return typedResourceStream(outerBundle, Bundle.class)
        .flatMap(b -> typedResourceStream(b, Patient.class))
        .map(Patient::getIdPart);
  }

  private static Bundle innerBundle(String patientId, String... policies) {
    var mainProvision = new Consent.ProvisionComponent();
    Stream.of(policies).forEach(p -> mainProvision.addProvision(permitProvision(p)));
    var consent =
        (Consent)
            new Consent()
                .setStatus(Consent.ConsentState.ACTIVE)
                .setProvision(mainProvision)
                .setId("consent-" + patientId);

    return Stream.of(new Patient().setId(patientId), consent).collect(toBundle());
  }

  /**
   * Parses the unreadable provision from JSON, as the gICS response is read, so HAPI keeps the date
   * as given.
   */
  private static Bundle innerBundleWithUnreadablePolicyB(String patientId) {
    var json =
        """
        {"resourceType": "Consent", "provision": {"provision": [{
          "type": "permit",
          "period": {"start": "2024-02-23T10:00:00", "end": "2054-01-31"},
          "code": [{"coding": [{"system": "%s", "code": "POLICY_B"}]}]
        }]}}\
        """
            .formatted(POLICY_SYSTEM);
    var unreadable =
        stringToFhirResource(Consent.class, json).getProvision().getProvisionFirstRep();
    var bundle = innerBundle(patientId, "POLICY_A");
    typedResourceStream(bundle, Consent.class)
        .forEach(c -> c.getProvision().addProvision(unreadable));
    return bundle;
  }

  private static Consent.ProvisionComponent permitProvision(String policy) {
    return new Consent.ProvisionComponent()
        .setType(Consent.ConsentProvisionType.PERMIT)
        .setPeriod(new Period().setStart(new Date(0)).setEnd(new Date(1)))
        .addCode(
            new CodeableConcept().addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode(policy)));
  }
}
