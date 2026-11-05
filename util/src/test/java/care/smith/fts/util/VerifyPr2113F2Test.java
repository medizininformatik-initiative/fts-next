package care.smith.fts.util;

import static care.smith.fts.util.ConsentedPatientExtractor.getPatientIdentifier;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class VerifyPr2113F2Test {

  @Test
  void identifierWithoutSystemDoesNotThrow() {
    var patient = new Patient();
    patient.addIdentifier(new Identifier().setValue("no-system"));
    patient.addIdentifier(new Identifier().setSystem("sys").setValue("p1"));
    var bundle = new Bundle();
    bundle.addEntry().setResource(patient);

    assertThatNoException().isThrownBy(() -> getPatientIdentifier("sys", bundle));
  }
}
