package care.smith.fts.tca.consent;

import static care.smith.fts.tca.consent.GicsFhirUtil.filterOuterBundle;
import static care.smith.fts.tca.consent.GicsFhirUtil.filterRequestedOuterBundle;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

/** Shows that patients without consent now reach the CDA extractor, with all identifiers. */
class VerifyPr2113F2Test {

  private static Bundle outerWithPatientWithoutConsent() {
    var patient = new Patient();
    patient.setId("Patient/1");
    patient.addIdentifier(new Identifier().setValue("no-system"));
    patient.addIdentifier(new Identifier().setSystem("sys").setValue("p1"));
    var inner = new Bundle();
    inner.addEntry().setResource(patient);
    var outer = new Bundle();
    outer.addEntry().setResource(inner);
    return outer;
  }

  @Test
  void fetchAllDropsPatientWithoutConsent() {
    var filtered = filterOuterBundle("policy-sys", Set.of("A"), outerWithPatientWithoutConsent());
    assertThat(filtered.getEntry()).isEmpty();
  }

  @Test
  void requestedKeepsPatientWithoutConsentWithSystemlessIdentifier() {
    var filtered =
        filterRequestedOuterBundle("policy-sys", Set.of("A"), outerWithPatientWithoutConsent());
    var inner = (Bundle) filtered.getEntryFirstRep().getResource();
    var patient = (Patient) inner.getEntryFirstRep().getResource();
    assertThat(patient.getIdentifierFirstRep().hasSystem()).isFalse();
  }
}
